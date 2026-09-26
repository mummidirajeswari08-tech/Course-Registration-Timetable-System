"""
===============================================================================
STUDENT RISK ANALYZER & ACADEMIC ADVISOR ALERT DISPATCHER
===============================================================================
Module: risk_analyzer.py
Description:
  Analyzes student attendance percentage and GPA metrics to calculate an
  empirical dropout-risk score (0.0 - 100.0), updates student risk flags,
  and automatically dispatches academic advisor alerts for high-risk students.
===============================================================================
"""

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import List, Dict, Any, Optional
import json
import sys


class RiskCategory(Enum):
    LOW = "LOW RISK"
    MODERATE = "MODERATE RISK"
    HIGH = "HIGH RISK"


@dataclass
class StudentAcademicRecord:
    student_id: str
    name: str
    major: str
    attendance_pct: float  # 0.0 - 100.0
    gpa: float             # 0.0 - 4.0
    advisor_name: str
    advisor_email: str
    completed_credits: int = 0


@dataclass
class RiskAssessmentResult:
    record: StudentAcademicRecord
    risk_score: float
    risk_category: RiskCategory
    alert_triggered: bool
    reasons: List[str]
    interventions: List[str]
    assessed_at: datetime = field(default_factory=datetime.now)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "student_id": self.record.student_id,
            "name": self.record.name,
            "attendance_pct": self.record.attendance_pct,
            "gpa": self.record.gpa,
            "risk_score": round(self.risk_score, 2),
            "risk_category": self.risk_category.value,
            "alert_triggered": self.alert_triggered,
            "reasons": self.reasons,
            "interventions": self.interventions,
            "assessed_at": self.assessed_at.isoformat()
        }


class StudentRiskAnalyzer:
    """
    Computes weighted student retention and dropout risk scores.
    Formula:
      Risk Score = w_att * (100 - Attendance) + w_gpa * ((4.0 - GPA) / 4.0 * 100)
    Critical Overrides:
      - Attendance < 75% triggers HIGH RISK flag.
      - GPA < 2.20 triggers HIGH RISK flag (Academic Probation).
    """

    def __init__(
        self,
        weight_attendance: float = 0.55,
        weight_gpa: float = 0.45,
        attendance_critical_threshold: float = 75.0,
        gpa_critical_threshold: float = 2.20,
        high_risk_score_threshold: float = 50.0,
        moderate_risk_score_threshold: float = 25.0
    ):
        self.weight_attendance = weight_attendance
        self.weight_gpa = weight_gpa
        self.attendance_critical_threshold = attendance_critical_threshold
        self.gpa_critical_threshold = gpa_critical_threshold
        self.high_risk_score_threshold = high_risk_score_threshold
        self.moderate_risk_score_threshold = moderate_risk_score_threshold

    def calculate_risk(self, record: StudentAcademicRecord) -> RiskAssessmentResult:
        """Evaluates a single student record and returns a detailed risk assessment."""
        # Validate boundary inputs
        attendance = max(0.0, min(100.0, float(record.attendance_pct)))
        gpa = max(0.0, min(4.0, float(record.gpa)))

        # 1. Metric Deficit Calculation
        attendance_deficit = max(0.0, 100.0 - attendance)
        gpa_deficit = max(0.0, ((4.0 - gpa) / 4.0) * 100.0)

        # 2. Weighted Score (Scale: 0.0 - 100.0)
        raw_score = (self.weight_attendance * attendance_deficit) + (self.weight_gpa * gpa_deficit)
        risk_score = min(100.0, max(0.0, round(raw_score, 2)))

        reasons = []
        interventions = []

        # 3. Rule-based Evaluation & Critical Overrides
        is_critical_attendance = attendance < self.attendance_critical_threshold
        is_critical_gpa = gpa < self.gpa_critical_threshold

        if is_critical_attendance:
            reasons.append(f"Severe attendance deficit ({attendance:.1f}% < {self.attendance_critical_threshold:.1f}% threshold)")
            interventions.append("Schedule attendance review meeting with Student Life dean")

        if is_critical_gpa:
            reasons.append(f"Cumulative GPA on academic warning/probation line ({gpa:.2f} < {self.gpa_critical_threshold:.2f})")
            interventions.append("Enroll in mandatory Peer Tutoring and Academic Skills Workshop")

        # Classify Risk Category
        if risk_score >= self.high_risk_score_threshold or is_critical_attendance or is_critical_gpa:
            category = RiskCategory.HIGH
            alert_triggered = True
            if not interventions:
                interventions.append("Immediate 1-on-1 advisor counseling session")
        elif risk_score >= self.moderate_risk_score_threshold or attendance < 85.0 or gpa < 3.0:
            category = RiskCategory.MODERATE
            alert_triggered = False
            reasons.append(f"Moderate performance drift (Risk Score: {risk_score:.1f})")
            interventions.append("Mid-semester check-in and study group recommendation")
        else:
            category = RiskCategory.LOW
            alert_triggered = False
            reasons.append("Good academic standing across attendance and grades")
            interventions.append("Continue standard degree progression")

        return RiskAssessmentResult(
            record=record,
            risk_score=risk_score,
            risk_category=category,
            alert_triggered=alert_triggered,
            reasons=reasons,
            interventions=interventions
        )

    def dispatch_advisor_alert(self, result: RiskAssessmentResult) -> None:
        """Simulates automated notification dispatch to the student's academic advisor."""
        rec = result.record
        print("+" + "-" * 78 + "+")
        print("| [DISPATCH ALERT] ACADEMIC RETENTION & ADVISORY ALERT NOTIFICATION")
        print("+" + "-" * 78 + "+")
        print(f"| TO:         {rec.advisor_name} <{rec.advisor_email}>")
        print(f"| STUDENT:    {rec.name} (ID: {rec.student_id}) | Major: {rec.major}")
        print(f"| TIMESTAMP:  {result.assessed_at.strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"| STATUS:     *** {result.risk_category.value} (Score: {result.risk_score:.1f}/100) ***")
        print("|")
        print("| KEY TRIGGER FACTORS:")
        for reason in result.reasons:
            print(f"|   * {reason}")
        print("|")
        print("| RECOMMENDED IMMEDIATE ACTION(S):")
        for act in result.interventions:
            print(f"|   -> {act}")
        print("+" + "-" * 78 + "+\n")

    def batch_assess(self, records: List[StudentAcademicRecord]) -> List[RiskAssessmentResult]:
        """Assesses a batch of student records and triggers alerts for high-risk profiles."""
        results = []
        print("\n" + "=" * 80)
        print(" BATCH STUDENT RISK ASSESSMENT REPORT")
        print("=" * 80)
        print(f"{'ID':<10} {'NAME':<18} {'ATTEND %':<10} {'GPA':<6} {'RISK SCORE':<12} {'STATUS':<15} {'ALERT'}")
        print("-" * 80)

        for record in records:
            res = self.calculate_risk(record)
            results.append(res)
            alert_flag = "[DISPATCHED]" if res.alert_triggered else "None"
            print(f"{record.student_id:<10} {record.name:<18} {record.attendance_pct:>6.1f}%   {record.gpa:>4.2f}  {res.risk_score:>8.1f}     {res.risk_category.value:<15} {alert_flag}")

        print("-" * 80)
        print(f"Total Evaluated: {len(results)} | High Risk Flagged: {sum(1 for r in results if r.alert_triggered)}\n")

        # Dispatch alerts for all flagged students
        for res in results:
            if res.alert_triggered:
                self.dispatch_advisor_alert(res)

        return results


# =============================================================================
# SAMPLE RUNNER & CLI DEMONSTRATION
# =============================================================================
if __name__ == "__main__":
    analyzer = StudentRiskAnalyzer()

    # Pre-configured dataset of sample students representing diverse scenarios
    sample_students = [
        StudentAcademicRecord(
            student_id="STU-1001",
            name="Alex Johnson",
            major="Computer Science",
            attendance_pct=68.0,
            gpa=2.30,
            advisor_name="Dr. Alan Turing",
            advisor_email="a.turing@university.edu",
            completed_credits=32
        ),
        StudentAcademicRecord(
            student_id="STU-1002",
            name="Sophia Davis",
            major="Mathematics",
            attendance_pct=96.5,
            gpa=3.85,
            advisor_name="Dr. Carl Gauss",
            advisor_email="c.gauss@university.edu",
            completed_credits=64
        ),
        StudentAcademicRecord(
            student_id="STU-1003",
            name="Marcus Chen",
            major="Computer Science",
            attendance_pct=72.0,
            gpa=3.10,
            advisor_name="Dr. Alan Turing",
            advisor_email="a.turing@university.edu",
            completed_credits=48
        ),
        StudentAcademicRecord(
            student_id="STU-1004",
            name="Emily Rodriguez",
            major="Electrical Eng",
            attendance_pct=82.0,
            gpa=2.05,
            advisor_name="Dr. Claude Shannon",
            advisor_email="c.shannon@university.edu",
            completed_credits=28
        )
    ]

    print("Running Student Risk Analytics Engine...")
    batch_results = analyzer.batch_assess(sample_students)

    # Export sample assessment summary as JSON for downstream integrations
    summary_export = [r.to_dict() for r in batch_results]
    print("\n[EXPORT SAMPLE JSON PAYLOAD]")
    print(json.dumps(summary_export[0], indent=2))
