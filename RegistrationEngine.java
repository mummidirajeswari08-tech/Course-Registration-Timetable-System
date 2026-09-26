import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ============================================================================
 * COURSE REGISTRATION & TIMETABLE SYSTEM - CORE ENGINE
 * ============================================================================
 * Implements:
 * 1. Directed Acyclic Graph (DAG) for Prerequisite Course Dependencies (ADSA)
 *    - Transitive prerequisite reachability
 *    - Cycle detection using 3-color DFS
 *    - Topological sorting (Kahn's Algorithm) for curriculum progression
 * 2. Interval-based Timetable Slot Conflict Detection
 * 3. Enrollment Transaction Validator (Credit limits, duplicate checks, etc.)
 * ============================================================================
 */
public class RegistrationEngine {

    // ========================================================================
    // 1. DATA STRUCTURES & MODELS
    // ========================================================================

    /**
     * Represents a scheduled academic time slot for a course offering.
     */
    public static class TimeSlot {
        private final DayOfWeek day;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final String room;

        public TimeSlot(DayOfWeek day, LocalTime startTime, LocalTime endTime, String room) {
            if (startTime.isAfter(endTime) || startTime.equals(endTime)) {
                throw new IllegalArgumentException("Start time must be strictly before end time: " + startTime + " - " + endTime);
            }
            this.day = day;
            this.startTime = startTime;
            this.endTime = endTime;
            this.room = room;
        }

        public DayOfWeek getDay() { return day; }
        public LocalTime getStartTime() { return startTime; }
        public LocalTime getEndTime() { return endTime; }
        public String getRoom() { return room; }

        /**
         * Checks whether this time slot overlaps with another time slot.
         * Interval clash condition:
         * Day matches AND (StartA < EndB) AND (StartB < EndA)
         */
        public boolean conflictsWith(TimeSlot other) {
            if (this.day != other.day) {
                return false;
            }
            return this.startTime.isBefore(other.endTime) && other.startTime.isBefore(this.endTime);
        }

        @Override
        public String toString() {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("hh:mm a");
            return day + " " + startTime.format(fmt) + " - " + endTime.format(fmt) + " (" + room + ")";
        }
    }

    /**
     * Represents an academic course node in the university curriculum.
     */
    public static class Course {
        private final String id;
        private final String title;
        private final int credits;
        private final List<TimeSlot> schedule;
        private final String instructor;

        public Course(String id, String title, int credits, String instructor) {
            this.id = id;
            this.title = title;
            this.credits = credits;
            this.instructor = instructor;
            this.schedule = new ArrayList<>();
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public int getCredits() { return credits; }
        public String getInstructor() { return instructor; }
        public List<TimeSlot> getSchedule() { return Collections.unmodifiableList(schedule); }

        public void addTimeSlot(TimeSlot slot) {
            this.schedule.add(slot);
        }

        @Override
        public String toString() {
            return id + ": " + title + " (" + credits + " credits, " + instructor + ")";
        }
    }

    /**
     * Represents an enrolled university student.
     */
    public static class Student {
        private final String studentId;
        private final String name;
        private final int maxCredits;
        private final Set<String> completedCourseIds;
        private final List<Course> enrolledCourses;

        public Student(String studentId, String name, int maxCredits) {
            this.studentId = studentId;
            this.name = name;
            this.maxCredits = maxCredits;
            this.completedCourseIds = new HashSet<>();
            this.enrolledCourses = new ArrayList<>();
        }

        public String getStudentId() { return studentId; }
        public String getName() { return name; }
        public int getMaxCredits() { return maxCredits; }
        public Set<String> getCompletedCourseIds() { return Collections.unmodifiableSet(completedCourseIds); }
        public List<Course> getEnrolledCourses() { return Collections.unmodifiableList(enrolledCourses); }

        public void addCompletedCourse(String courseId) {
            completedCourseIds.add(courseId);
        }

        public int getTotalEnrolledCredits() {
            return enrolledCourses.stream().mapToInt(Course::getCredits).sum();
        }

        public boolean isEnrolledIn(String courseId) {
            return enrolledCourses.stream().anyMatch(c -> c.getId().equalsIgnoreCase(courseId));
        }

        public void enrollCourse(Course course) {
            enrolledCourses.add(course);
        }

        public boolean dropCourse(String courseId) {
            return enrolledCourses.removeIf(c -> c.getId().equalsIgnoreCase(courseId));
        }
    }

    /**
     * Result container for enrollment transactions.
     */
    public static class EnrollmentResult {
        public enum Status {
            SUCCESS,
            ALREADY_ENROLLED,
            PREREQUISITE_NOT_MET,
            TIMETABLE_CLASH,
            CREDIT_LIMIT_EXCEEDED
        }

        private final Status status;
        private final String message;
        private final String conflictingDetails;

        public EnrollmentResult(Status status, String message, String conflictingDetails) {
            this.status = status;
            this.message = message;
            this.conflictingDetails = conflictingDetails;
        }

        public Status getStatus() { return status; }
        public String getMessage() { return message; }
        public String getConflictingDetails() { return conflictingDetails; }
        public boolean isSuccess() { return status == Status.SUCCESS; }

        @Override
        public String toString() {
            return "[" + status + "] " + message + (conflictingDetails != null ? " (" + conflictingDetails + ")" : "");
        }
    }

    // ========================================================================
    // 2. DIRECTED ACYCLIC GRAPH (DAG) PREREQUISITE ENGINE
    // ========================================================================

    /**
     * Manages prerequisite relationships as a Directed Acyclic Graph (DAG).
     * Direction: Course -> Required Prerequisites
     */
    public static class PrerequisiteDAG {
        // courseId -> Set of direct prerequisite courseIds
        private final Map<String, Set<String>> prereqMap = new HashMap<>();
        // All known courses registered in DAG
        private final Set<String> allCourses = new HashSet<>();

        public void addCourse(String courseId) {
            allCourses.add(courseId);
            prereqMap.putIfAbsent(courseId, new HashSet<>());
        }

        /**
         * Adds a prerequisite dependency: 'courseId' requires 'prereqCourseId'.
         * Validates that this dependency does not introduce a directed cycle.
         */
        public void addPrerequisite(String courseId, String prereqCourseId) {
            addCourse(courseId);
            addCourse(prereqCourseId);

            // Temporarily add edge
            prereqMap.get(courseId).add(prereqCourseId);

            // Cycle check (Must remain a DAG)
            if (hasCycle()) {
                // Revert edge and reject
                prereqMap.get(courseId).remove(prereqCourseId);
                throw new IllegalStateException("Circular prerequisite dependency detected! Cannot make " 
                        + prereqCourseId + " a prerequisite of " + courseId);
            }
        }

        public Set<String> getDirectPrerequisites(String courseId) {
            return prereqMap.getOrDefault(courseId, Collections.emptySet());
        }

        /**
         * Recursively finds ALL transitive prerequisites for a course using BFS.
         */
        public Set<String> getAllTransitivePrerequisites(String courseId) {
            Set<String> visited = new HashSet<>();
            Queue<String> queue = new LinkedList<>();

            for (String direct : getDirectPrerequisites(courseId)) {
                queue.add(direct);
                visited.add(direct);
            }

            while (!queue.isEmpty()) {
                String current = queue.poll();
                for (String parent : getDirectPrerequisites(current)) {
                    if (!visited.contains(parent)) {
                        visited.add(parent);
                        queue.add(parent);
                    }
                }
            }
            return visited;
        }

        /**
         * Cycle detection using 3-color DFS (WHITE=unvisited, GRAY=visiting, BLACK=visited).
         */
        public boolean hasCycle() {
            Map<String, Integer> state = new HashMap<>(); // 0: White, 1: Gray, 2: Black
            for (String node : allCourses) {
                state.put(node, 0);
            }

            for (String node : allCourses) {
                if (state.get(node) == 0) {
                    if (dfsCheckCycle(node, state)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean dfsCheckCycle(String current, Map<String, Integer> state) {
            state.put(current, 1); // Mark as Gray (currently on recursion stack)

            for (String neighbor : prereqMap.getOrDefault(current, Collections.emptySet())) {
                Integer neighborState = state.getOrDefault(neighbor, 0);
                if (neighborState == 1) {
                    // Back-edge detected -> Cycle found!
                    return true;
                }
                if (neighborState == 0) {
                    if (dfsCheckCycle(neighbor, state)) {
                        return true;
                    }
                }
            }

            state.put(current, 2); // Mark as Black (fully processed)
            return false;
        }

        /**
         * Produces a topological sort of courses (Curriculum Progression Order)
         * using Kahn's Algorithm.
         */
        public List<String> getCurriculumSequence() {
            // Compute in-degrees where edge: prereq -> course
            Map<String, Integer> inDegree = new HashMap<>();
            Map<String, List<String>> dependents = new HashMap<>();

            for (String c : allCourses) {
                inDegree.put(c, 0);
                dependents.put(c, new ArrayList<>());
            }

            for (Map.Entry<String, Set<String>> entry : prereqMap.entrySet()) {
                String course = entry.getKey();
                for (String prereq : entry.getValue()) {
                    dependents.get(prereq).add(course);
                    inDegree.put(course, inDegree.get(course) + 1);
                }
            }

            Queue<String> queue = new LinkedList<>();
            for (String c : allCourses) {
                if (inDegree.get(c) == 0) {
                    queue.add(c);
                }
            }

            List<String> order = new ArrayList<>();
            while (!queue.isEmpty()) {
                String node = queue.poll();
                order.add(node);

                for (String nextCourse : dependents.get(node)) {
                    inDegree.put(nextCourse, inDegree.get(nextCourse) - 1);
                    if (inDegree.get(nextCourse) == 0) {
                        queue.add(nextCourse);
                    }
                }
            }

            if (order.size() != allCourses.size()) {
                throw new IllegalStateException("Curriculum has cycles; topological ordering impossible.");
            }
            return order;
        }
    }

    // ========================================================================
    // 3. REGISTRATION VALIDATION ENGINE
    // ========================================================================

    private final PrerequisiteDAG prerequisiteDAG;
    private final Map<String, Course> courseCatalog;

    public RegistrationEngine() {
        this.prerequisiteDAG = new PrerequisiteDAG();
        this.courseCatalog = new HashMap<>();
    }

    public PrerequisiteDAG getPrerequisiteDAG() {
        return prerequisiteDAG;
    }

    public void addCourseToCatalog(Course course) {
        courseCatalog.put(course.getId(), course);
        prerequisiteDAG.addCourse(course.getId());
    }

    public void addPrerequisite(String courseId, String prereqId) {
        prerequisiteDAG.addPrerequisite(courseId, prereqId);
    }

    /**
     * Validates and executes an enrollment transaction.
     * Enforces:
     * 1. Duplicate enrollment check
     * 2. DAG Prerequisite check (transitive dependencies)
     * 3. Timetable slot conflict check
     * 4. Maximum credit limit check
     */
    public EnrollmentResult validateAndEnroll(Student student, String courseId) {
        Course course = courseCatalog.get(courseId);
        if (course == null) {
            return new EnrollmentResult(EnrollmentResult.Status.ALREADY_ENROLLED, 
                    "Course not found in catalog: " + courseId, null);
        }

        // Rule 1: Duplicate Registration
        if (student.isEnrolledIn(courseId)) {
            return new EnrollmentResult(
                EnrollmentResult.Status.ALREADY_ENROLLED,
                "Student is already registered for " + courseId,
                null
            );
        }

        // Rule 2: DAG Prerequisite Check
        Set<String> requiredPrereqs = prerequisiteDAG.getDirectPrerequisites(courseId);
        List<String> missingPrereqs = new ArrayList<>();
        for (String req : requiredPrereqs) {
            if (!student.getCompletedCourseIds().contains(req)) {
                missingPrereqs.add(req);
            }
        }

        if (!missingPrereqs.isEmpty()) {
            return new EnrollmentResult(
                EnrollmentResult.Status.PREREQUISITE_NOT_MET,
                "Prerequisite violation (DAG rule): Missing required prerequisite(s)",
                String.join(", ", missingPrereqs)
            );
        }

        // Rule 3: Timetable Clash Detection
        for (Course enrolled : student.getEnrolledCourses()) {
            for (TimeSlot newSlot : course.getSchedule()) {
                for (TimeSlot existingSlot : enrolled.getSchedule()) {
                    if (newSlot.conflictsWith(existingSlot)) {
                        return new EnrollmentResult(
                            EnrollmentResult.Status.TIMETABLE_CLASH,
                            "Timetable clash detected with currently enrolled course: " + enrolled.getId(),
                            "Clash at " + newSlot.getDay() + " " + newSlot.getStartTime() + " with " + existingSlot
                        );
                    }
                }
            }
        }

        // Rule 4: Maximum Credit Load Check
        if (student.getTotalEnrolledCredits() + course.getCredits() > student.getMaxCredits()) {
            return new EnrollmentResult(
                EnrollmentResult.Status.CREDIT_LIMIT_EXCEEDED,
                "Credit limit exceeded: Attempting to register " + (student.getTotalEnrolledCredits() + course.getCredits())
                + " credits, max allowed is " + student.getMaxCredits(),
                null
            );
        }

        // All checks passed -> Enroll student
        student.enrollCourse(course);
        return new EnrollmentResult(
            EnrollmentResult.Status.SUCCESS,
            "Successfully enrolled in " + course.getId() + " - " + course.getTitle(),
            course.getSchedule().toString()
        );
    }

    // ========================================================================
    // 4. MAIN TEST RUNNER & VERIFICATION
    // ========================================================================

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("  ACADEMIC COURSE REGISTRATION & TIMETABLE VALIDATION ENGINE");
        System.out.println("================================================================================\n");

        RegistrationEngine engine = new RegistrationEngine();

        // 1. Setup Course Offerings & Timetable Slots
        Course cs101 = new Course("CS101", "Introduction to Computer Science", 4, "Dr. Hopper");
        cs101.addTimeSlot(new TimeSlot(DayOfWeek.MONDAY, LocalTime.of(8, 30), LocalTime.of(10, 0), "Hall-101"));

        Course cs201 = new Course("CS201", "Data Structures & Algorithms", 4, "Dr. Turing");
        cs201.addTimeSlot(new TimeSlot(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 30), "Hall-A"));

        Course cs301 = new Course("CS301", "Advanced Algorithms", 4, "Dr. Knuth");
        cs301.addTimeSlot(new TimeSlot(DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(11, 30), "Lab-3"));

        Course math101 = new Course("MATH101", "Discrete Mathematics", 3, "Dr. Gauss");
        // NOTE: Intentionally clashes with CS201 on Monday 10:00 - 11:30 AM!
        math101.addTimeSlot(new TimeSlot(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 30), "Math-201"));

        Course math201 = new Course("MATH201", "Linear Algebra", 3, "Dr. Noether");
        math201.addTimeSlot(new TimeSlot(DayOfWeek.THURSDAY, LocalTime.of(13, 0), LocalTime.of(14, 30), "Math-104"));

        engine.addCourseToCatalog(cs101);
        engine.addCourseToCatalog(cs201);
        engine.addCourseToCatalog(cs301);
        engine.addCourseToCatalog(math101);
        engine.addCourseToCatalog(math201);

        // 2. Build Prerequisite DAG
        // CS201 requires CS101
        engine.addPrerequisite("CS201", "CS101");
        // CS301 requires CS201
        engine.addPrerequisite("CS301", "CS201");
        // MATH201 requires MATH101
        engine.addPrerequisite("MATH201", "MATH101");

        // Print Topological Sort of Curriculum
        System.out.println("--- [TEST 1] CURRICULUM TOPOLOGICAL SORT (RECOMMENDED STUDY SEQUENCE) ---");
        List<String> sequence = engine.getPrerequisiteDAG().getCurriculumSequence();
        System.out.println("Topological Ordering: " + sequence);
        System.out.println();

        // 3. Test Cycle Detection
        System.out.println("--- [TEST 2] DAG CYCLE DETECTION VERIFICATION ---");
        try {
            System.out.println("Attempting to add circular dependency: CS101 requires CS301 (Creating CS101->CS301->CS201->CS101)...");
            engine.addPrerequisite("CS101", "CS301");
            System.out.println("FAILED: Cycle was not caught!");
        } catch (IllegalStateException e) {
            System.out.println("SUCCESS: Caught expected cycle violation -> " + e.getMessage());
        }
        System.out.println();

        // 4. Student Enrollment Simulations
        System.out.println("--- [TEST 3] STUDENT ENROLLMENT TRANSACTION TESTS ---");
        Student student = new Student("STU-1001", "Alex Johnson", 18);
        student.addCompletedCourse("CS101"); // Alex completed CS101 in previous semester

        System.out.println("Student Profile: " + student.getName() + " (" + student.getStudentId() + ")");
        System.out.println("Completed Courses: " + student.getCompletedCourseIds());
        System.out.println();

        // Case A: Valid Enrollment (CS201 prereq CS101 is satisfied)
        System.out.println("Scenario A: Registering for CS201 (Prereq: CS101)...");
        EnrollmentResult resA = engine.validateAndEnroll(student, "CS201");
        System.out.println("Result: " + resA);
        System.out.println("Current Credits: " + student.getTotalEnrolledCredits() + " / " + student.getMaxCredits());
        System.out.println();

        // Case B: Prerequisite Violation (CS301 requires CS201, which is not yet completed)
        System.out.println("Scenario B: Registering for CS301 (Requires CS201 completed)...");
        EnrollmentResult resB = engine.validateAndEnroll(student, "CS301");
        System.out.println("Result: " + resB);
        System.out.println();

        // Case C: Timetable Slot Conflict (MATH101 clashes with CS201 on Mon 10:00 AM)
        System.out.println("Scenario C: Registering for MATH101 (Monday 10:00 AM - Clashes with CS201)...");
        EnrollmentResult resC = engine.validateAndEnroll(student, "MATH101");
        System.out.println("Result: " + resC);
        System.out.println();

        // Case D: Valid Non-conflicting Enrollment
        System.out.println("Scenario D: Student finishes MATH101 in summer and registers for MATH201...");
        student.addCompletedCourse("MATH101");
        EnrollmentResult resD = engine.validateAndEnroll(student, "MATH201");
        System.out.println("Result: " + resD);
        System.out.println("Current Credits: " + student.getTotalEnrolledCredits() + " / " + student.getMaxCredits());
        System.out.println();

        // Summary of Enrolled Schedule
        System.out.println("--- FINAL ENROLLED SCHEDULE FOR " + student.getName() + " ---");
        for (Course c : student.getEnrolledCourses()) {
            System.out.println("- " + c);
            for (TimeSlot s : c.getSchedule()) {
                System.out.println("    " + s);
            }
        }
        System.out.println("\n================================================================================");
    }
}
