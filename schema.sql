-- ============================================================================
-- UNIVERSITY COURSE REGISTRATION & TIMETABLE SYSTEM
-- Relational Database Management System (DBMS) Schema & Seed Data
-- ============================================================================
-- Compatible with ANSI SQL, PostgreSQL, MySQL 8+, SQLite 3
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. DROP EXISTING TABLES (REVERSE DEPENDENCY ORDER)
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS risk_assessments;
DROP TABLE IF EXISTS enrollments;
DROP TABLE IF EXISTS course_sections;
DROP TABLE IF EXISTS course_prerequisites;
DROP TABLE IF EXISTS courses;
DROP TABLE IF EXISTS students;
DROP TABLE IF EXISTS faculty;
DROP TABLE IF EXISTS departments;

-- ----------------------------------------------------------------------------
-- 2. SCHEMA DEFINITION WITH INTEGRITY CONSTRAINTS
-- ----------------------------------------------------------------------------

-- Table: departments
CREATE TABLE departments (
    dept_id         VARCHAR(10)     NOT NULL,
    dept_name       VARCHAR(100)    NOT NULL,
    building        VARCHAR(50)     NOT NULL,
    CONSTRAINT pk_departments PRIMARY KEY (dept_id)
);

-- Table: faculty (Academic Advisors & Course Instructors)
CREATE TABLE faculty (
    faculty_id      VARCHAR(20)     NOT NULL,
    full_name       VARCHAR(100)    NOT NULL,
    email           VARCHAR(100)    NOT NULL UNIQUE,
    office          VARCHAR(50)     NOT NULL,
    dept_id         VARCHAR(10)     NOT NULL,
    CONSTRAINT pk_faculty PRIMARY KEY (faculty_id),
    CONSTRAINT fk_faculty_department FOREIGN KEY (dept_id) 
        REFERENCES departments(dept_id) ON DELETE RESTRICT
);

-- Table: students (Academic Profiles & Retention Metrics)
CREATE TABLE students (
    student_id      VARCHAR(20)     NOT NULL,
    first_name      VARCHAR(50)     NOT NULL,
    last_name       VARCHAR(50)     NOT NULL,
    email           VARCHAR(100)    NOT NULL UNIQUE,
    dept_id         VARCHAR(10)     NOT NULL,
    admission_year  INT             NOT NULL,
    max_credits     INT             DEFAULT 18 CHECK (max_credits >= 12 AND max_credits <= 24),
    attendance_pct  DECIMAL(5, 2)   DEFAULT 100.00 CHECK (attendance_pct >= 0.00 AND attendance_pct <= 100.00),
    cumulative_gpa  DECIMAL(3, 2)   DEFAULT 4.00 CHECK (cumulative_gpa >= 0.00 AND cumulative_gpa <= 4.00),
    risk_flag       VARCHAR(20)     DEFAULT 'LOW' CHECK (risk_flag IN ('LOW', 'MODERATE', 'HIGH')),
    advisor_id      VARCHAR(20)     NOT NULL,
    CONSTRAINT pk_students PRIMARY KEY (student_id),
    CONSTRAINT fk_students_dept FOREIGN KEY (dept_id) 
        REFERENCES departments(dept_id) ON DELETE RESTRICT,
    CONSTRAINT fk_students_advisor FOREIGN KEY (advisor_id) 
        REFERENCES faculty(faculty_id) ON DELETE RESTRICT
);

-- Table: courses
CREATE TABLE courses (
    course_id       VARCHAR(20)     NOT NULL,
    title           VARCHAR(150)    NOT NULL,
    credits         INT             NOT NULL CHECK (credits >= 1 AND credits <= 6),
    dept_id         VARCHAR(10)     NOT NULL,
    description     TEXT,
    CONSTRAINT pk_courses PRIMARY KEY (course_id),
    CONSTRAINT fk_courses_dept FOREIGN KEY (dept_id) 
        REFERENCES departments(dept_id) ON DELETE RESTRICT
);

-- Table: course_prerequisites (Directed Acyclic Graph Edges)
-- Edge direction: course_id REQUIRES prereq_course_id
CREATE TABLE course_prerequisites (
    course_id           VARCHAR(20)     NOT NULL,
    prereq_course_id    VARCHAR(20)     NOT NULL,
    CONSTRAINT pk_course_prerequisites PRIMARY KEY (course_id, prereq_course_id),
    CONSTRAINT fk_prereq_course FOREIGN KEY (course_id) 
        REFERENCES courses(course_id) ON DELETE CASCADE,
    CONSTRAINT fk_prereq_target FOREIGN KEY (prereq_course_id) 
        REFERENCES courses(course_id) ON DELETE RESTRICT,
    CONSTRAINT chk_no_self_prerequisite CHECK (course_id <> prereq_course_id)
);

-- Table: course_sections (Timetable Slots & Offerings)
CREATE TABLE course_sections (
    section_id      INT             NOT NULL,
    course_id       VARCHAR(20)     NOT NULL,
    faculty_id      VARCHAR(20)     NOT NULL,
    semester        VARCHAR(20)     NOT NULL,
    academic_year   INT             NOT NULL,
    day_of_week     VARCHAR(10)     NOT NULL CHECK (day_of_week IN ('Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday')),
    start_time      TIME            NOT NULL,
    end_time        TIME            NOT NULL,
    room            VARCHAR(50)     NOT NULL,
    max_capacity    INT             DEFAULT 30 CHECK (max_capacity > 0),
    CONSTRAINT pk_course_sections PRIMARY KEY (section_id),
    CONSTRAINT fk_sections_course FOREIGN KEY (course_id) 
        REFERENCES courses(course_id) ON DELETE CASCADE,
    CONSTRAINT fk_sections_faculty FOREIGN KEY (faculty_id) 
        REFERENCES faculty(faculty_id) ON DELETE RESTRICT,
    CONSTRAINT chk_slot_time_interval CHECK (start_time < end_time)
);

-- Table: enrollments (Student Course Registrations)
CREATE TABLE enrollments (
    enrollment_id   INT             NOT NULL,
    student_id      VARCHAR(20)     NOT NULL,
    section_id      INT             NOT NULL,
    enrollment_date DATE            NOT NULL,
    status          VARCHAR(20)     DEFAULT 'ENROLLED' CHECK (status IN ('ENROLLED', 'DROPPED', 'COMPLETED', 'FAILED')),
    grade           VARCHAR(5)      CHECK (grade IN ('A+', 'A', 'A-', 'B+', 'B', 'B-', 'C+', 'C', 'C-', 'D', 'F', 'I', 'W')),
    CONSTRAINT pk_enrollments PRIMARY KEY (enrollment_id),
    CONSTRAINT uq_student_section UNIQUE (student_id, section_id),
    CONSTRAINT fk_enrollments_student FOREIGN KEY (student_id) 
        REFERENCES students(student_id) ON DELETE CASCADE,
    CONSTRAINT fk_enrollments_section FOREIGN KEY (section_id) 
        REFERENCES course_sections(section_id) ON DELETE RESTRICT
);

-- Table: risk_assessments (Analytics Log & Advisor Alerts)
CREATE TABLE risk_assessments (
    assessment_id   INT             NOT NULL,
    student_id      VARCHAR(20)     NOT NULL,
    attendance_pct  DECIMAL(5, 2)   NOT NULL,
    cumulative_gpa  DECIMAL(3, 2)   NOT NULL,
    risk_score      DECIMAL(5, 2)   NOT NULL,
    risk_flag       VARCHAR(20)     NOT NULL CHECK (risk_flag IN ('LOW', 'MODERATE', 'HIGH')),
    advisor_alerted BOOLEAN         DEFAULT FALSE,
    assessed_at     TIMESTAMP       NOT NULL,
    CONSTRAINT pk_risk_assessments PRIMARY KEY (assessment_id),
    CONSTRAINT fk_risk_student FOREIGN KEY (student_id) 
        REFERENCES students(student_id) ON DELETE CASCADE
);

-- ----------------------------------------------------------------------------
-- 3. INDEXES FOR QUERY OPTIMIZATION
-- ----------------------------------------------------------------------------
CREATE INDEX idx_students_dept ON students(dept_id);
CREATE INDEX idx_students_advisor ON students(advisor_id);
CREATE INDEX idx_courses_dept ON courses(dept_id);
CREATE INDEX idx_prereq_course ON course_prerequisites(course_id);
CREATE INDEX idx_sections_schedule ON course_sections(day_of_week, start_time, end_time);
CREATE INDEX idx_enrollments_student ON enrollments(student_id);
CREATE INDEX idx_enrollments_section ON enrollments(section_id);
CREATE INDEX idx_risk_student ON risk_assessments(student_id);

-- ----------------------------------------------------------------------------
-- 4. SEED DATA
-- ----------------------------------------------------------------------------

-- Departments
INSERT INTO departments (dept_id, dept_name, building) VALUES
('CS', 'Computer Science & Engineering', 'Turing Hall'),
('MATH', 'Mathematics & Statistics', 'Gauss Science Center'),
('ENG', 'Humanities & Communication', 'Shakespeare Memorial Building');

-- Faculty
INSERT INTO faculty (faculty_id, full_name, email, office, dept_id) VALUES
('FAC-101', 'Dr. Alan Turing', 'a.turing@university.edu', 'TH-401', 'CS'),
('FAC-102', 'Dr. Donald Knuth', 'd.knuth@university.edu', 'TH-405', 'CS'),
('FAC-103', 'Dr. Edgar Codd', 'e.codd@university.edu', 'TH-310', 'CS'),
('FAC-104', 'Dr. Carl Gauss', 'c.gauss@university.edu', 'GC-201', 'MATH'),
('FAC-105', 'Dr. Emmy Noether', 'e.noether@university.edu', 'GC-204', 'MATH'),
('FAC-106', 'Prof. Sarah Blake', 's.blake@university.edu', 'SB-102', 'ENG');

-- Students
INSERT INTO students (student_id, first_name, last_name, email, dept_id, admission_year, max_credits, attendance_pct, cumulative_gpa, risk_flag, advisor_id) VALUES
('STU-1001', 'Alex', 'Johnson', 'alex.j@student.university.edu', 'CS', 2024, 18, 68.00, 2.30, 'HIGH', 'FAC-101'),
('STU-1002', 'Sophia', 'Davis', 'sophia.d@student.university.edu', 'MATH', 2023, 20, 96.50, 3.85, 'LOW', 'FAC-104'),
('STU-1003', 'Marcus', 'Chen', 'marcus.c@student.university.edu', 'CS', 2024, 18, 72.00, 3.10, 'HIGH', 'FAC-101'),
('STU-1004', 'Emily', 'Rodriguez', 'emily.r@student.university.edu', 'CS', 2025, 18, 82.00, 2.05, 'HIGH', 'FAC-102');

-- Courses
INSERT INTO courses (course_id, title, credits, dept_id, description) VALUES
('CS101', 'Introduction to Computer Science', 4, 'CS', 'Fundamental programming concepts, procedural paradigms, and algorithmic problem solving.'),
('CS201', 'Data Structures & Algorithms', 4, 'CS', 'Stacks, queues, trees, graphs, sorting, searching, and asymptotic Big-O runtime analysis.'),
('CS301', 'Advanced Algorithms', 4, 'CS', 'Dynamic programming, greedy strategies, NP-completeness, and network flow optimization.'),
('CS350', 'Database Management Systems', 3, 'CS', 'Relational algebra, SQL, normalization, concurrency control, and transactions.'),
('MATH101', 'Discrete Mathematics', 3, 'MATH', 'Propositional logic, set theory, graph theory, combinatorics, and proof techniques.'),
('MATH201', 'Linear Algebra & Applications', 3, 'MATH', 'Vector spaces, matrices, determinants, eigenvalues, eigenvectors, and transformations.'),
('ENG102', 'Technical Communication', 2, 'ENG', 'Professional technical report writing, engineering documentation, and oral presentations.');

-- DAG Prerequisites:
-- CS201 requires CS101
-- CS301 requires CS201
-- CS350 requires CS201
-- MATH201 requires MATH101
INSERT INTO course_prerequisites (course_id, prereq_course_id) VALUES
('CS201', 'CS101'),
('CS301', 'CS201'),
('CS350', 'CS201'),
('MATH201', 'MATH101');

-- Course Sections & Timetable Slots (Fall 2026)
-- Note: Section 501 (CS201) and Section 504 (MATH101) both meet on Monday 10:00 - 11:30 AM (Clash case!)
INSERT INTO course_sections (section_id, course_id, faculty_id, semester, academic_year, day_of_week, start_time, end_time, room, max_capacity) VALUES
(501, 'CS201', 'FAC-101', 'Fall', 2026, 'Monday',    '10:00:00', '11:30:00', 'Hall-A', 40),
(502, 'CS301', 'FAC-102', 'Fall', 2026, 'Tuesday',   '10:00:00', '11:30:00', 'Lab-3', 30),
(503, 'CS350', 'FAC-103', 'Fall', 2026, 'Monday',    '14:00:00', '15:30:00', 'Hall-B', 45),
(504, 'MATH101', 'FAC-104', 'Fall', 2026, 'Monday',  '10:00:00', '11:30:00', 'Math-201', 50),
(505, 'MATH201', 'FAC-105', 'Fall', 2026, 'Thursday','13:00:00', '14:30:00', 'Math-104', 35),
(506, 'ENG102', 'FAC-106', 'Fall', 2026, 'Friday',   '11:00:00', '12:30:00', 'Hum-101', 25);

-- Historical Completed Enrollments & Active Enrollments
-- Alex Johnson previously completed CS101 with grade 'B+'
INSERT INTO enrollments (enrollment_id, student_id, section_id, enrollment_date, status, grade) VALUES
(1001, 'STU-1001', 501, '2026-08-25', 'ENROLLED', NULL);

-- Initial Risk Assessment Log for Alex Johnson
INSERT INTO risk_assessments (assessment_id, student_id, attendance_pct, cumulative_gpa, risk_score, risk_flag, advisor_alerted, assessed_at) VALUES
(1, 'STU-1001', 68.00, 2.30, 36.73, 'HIGH', TRUE, '2026-09-22 02:00:00');

-- ----------------------------------------------------------------------------
-- 5. USEFUL VALIDATION QUERIES
-- ----------------------------------------------------------------------------

-- A. Find all direct and indirect prerequisites for a course (Recursive Common Table Expression - CTE)
-- Example: Query all prerequisites for CS301
/*
WITH RECURSIVE CourseHierarchy AS (
    SELECT course_id, prereq_course_id, 1 AS depth
    FROM course_prerequisites
    WHERE course_id = 'CS301'
    UNION ALL
    SELECT cp.course_id, cp.prereq_course_id, ch.depth + 1
    FROM course_prerequisites cp
    INNER JOIN CourseHierarchy ch ON cp.course_id = ch.prereq_course_id
)
SELECT * FROM CourseHierarchy;
*/

-- B. Detect Timetable Clashes between any two active sections
/*
SELECT 
    s1.section_id AS sec1, s1.course_id AS course1,
    s2.section_id AS sec2, s2.course_id AS course2,
    s1.day_of_week, s1.start_time, s1.end_time
FROM course_sections s1
JOIN course_sections s2 
  ON s1.section_id < s2.section_id
 AND s1.day_of_week = s2.day_of_week
 AND s1.start_time < s2.end_time
 AND s2.start_time < s1.end_time;
*/
