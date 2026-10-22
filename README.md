# Course Registration and Waitlist System

A bounded, thread-safe Spring Boot service slice implementing course registration, dropping, and FIFO waitlist promotion with an in-memory data store.

Built for the **Focused AI-Assisted Engineering Assessment: Course Waitlist**.

---

## 1. Feature Overview & Bounded Scope

This project implements a single course offering's complete registration lifecycle:
- **Direct Enrollment**: When capacity remains and the learner satisfies prerequisite requirements.
- **FIFO Waitlisting**: When the course offering reaches maximum capacity, eligible learners join the waitlist in strict First-In, First-Out order without duplicate entries.
- **Drop & Automated Promotion**: When an enrolled learner drops, a seat opens and the earliest eligible waitlisted learner is promoted automatically.
- **In-Memory Storage**: State is held in thread-safe in-memory collections without an external database.
- **Strict Scope Boundaries**:
  - Scoped to one course offering at a time.
  - No frontend, external database, authentication/authorization, payments, course catalog browsing, multi-term registration, or cloud deployment infrastructure.

---

## 2. Documented Policies & Business Rules

### 2.1 Prerequisite Rule
- Each course offering specifies prerequisite requirements (e.g., prerequisite course codes).
- A learner profile records the set of completed prerequisite courses.
- **Enrollment Rule**: A learner is eligible if and only if their completed courses satisfy all prerequisites required by the course offering ($\text{prerequisites} \subseteq \text{learner.completedCourses}$).
- An ineligible learner attempting direct enrollment is rejected immediately with a descriptive status (`PREREQUISITE_NOT_MET`) without mutating state.

### 2.2 Waitlist & Ineligible Learner Policy ("Skip and Retain")
- **Joining the Waitlist**:
  - When capacity is full, only eligible learners can enter the waitlist.
  - Ineligible learners attempting to join the waitlist are rejected (`PREREQUISITE_NOT_MET`).
  - Duplicate requests are rejected idempotently (`ALREADY_WAITLISTED` or `ALREADY_ENROLLED`).
- **Waitlist Promotion Policy ("Skip and Retain")**:
  - When an enrolled learner drops, a seat opens.
  - The waitlist is traversed in strict FIFO order from head to tail to locate the earliest eligible learner.
  - If a waitlisted candidate is **eligible**: They are dequeued from the waitlist, promoted into the opened seat, and marked enrolled.
  - If a waitlisted candidate is **ineligible** (e.g., prerequisite revoked or profile changes):
    - **Policy Applied: Skip and Retain**: The ineligible learner is skipped for this promotion so that the next earliest *eligible* learner receives the seat. The ineligible learner **remains waiting in their existing queue position**, allowing them to be promoted if their eligibility is fulfilled when a subsequent seat opens.
    - *Rationale*: Preserves fairness in queue priority without blocking other eligible candidates from filling available capacity.
  - If **no eligible learner exists** or the waitlist is empty: The opened seat remains vacant.

### 2.3 Dropping & Idempotency Rules
- **Dropping an Enrolled Learner**: Frees an enrolled seat and triggers immediate FIFO promotion of the next eligible waitlisted learner.
- **Invalid / Repeated Drops**: Dropping an unregistered learner or duplicate drop requests return a clean `404 Not Found` result (`NOT_ENROLLED`) without corrupting enrolled counts, capacity limits, or waitlist order.

---

## 3. Invariants & Thread Safety

| Invariant | Guarantee |
| :--- | :--- |
| **Capacity Limit** | $0 \le \text{enrolledCount} \le \text{capacity}$ at all times. Available capacity is derived as $\max(0, \text{capacity} - \text{enrolledCount})$. |
| **Mutual Exclusivity** | A learner cannot be both enrolled and on the waitlist simultaneously ($\text{Enrolled} \cap \text{Waitlist} = \emptyset$). |
| **No Duplicates** | A learner can appear at most once across the enrolled set and the waitlist queue. |
| **Strict FIFO Ordering** | Waitlist entries maintain arrival order based on queue position. |
| **Atomic Transitions** | Enrollment, drop, and promotion occur inside synchronized critical sections per course offering to prevent race conditions during concurrent access. |

---

## 4. Technology Stack & Prerequisites

- **Language**: Java 25 (OpenJDK 25 / Oracle JDK 25)
- **Framework**: Spring Boot 4.1.0 (Spring Framework 7.0.8)
- **Servlet Container**: Embedded Tomcat 11.0.22
- **Validation**: Jakarta Bean Validation API 3.1.1 (Hibernate Validator 9.1.0.Final)
- **Testing**: JUnit Jupiter 6.0.3, AssertJ 3.27.7, Mockito 5.23.0, Spring Boot Test
- **Build Tool**: Apache Maven 3.9.16 (Maven Wrapper `./mvnw` provided)
- **Persistence**: In-memory concurrent collections (`ConcurrentHashMap`, `LinkedHashSet`, `LinkedList`)

### 4.1 Environment Setup
Ensure JDK 25 is installed and configured in your environment:
```bash
java -version
```
Expected output:
```text
java version "25.0.4" 2026-07-21 LTS
Java(TM) SE Runtime Environment (build 25.0.4+7-LTS-189)
```

Ensure `JAVA_HOME` is exported:
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
```

---

## 5. Build, Test, and Run Commands

### 5.1 Compiling and Packaging
To compile and package the application jar using the bundled Maven Wrapper:
```bash
./mvnw clean package
```

### 5.2 Running the Full Test Suite
Execute all unit, integration, and concurrency tests:
```bash
./mvnw clean test
```

### 5.3 Running the Application
Start the embedded Spring Boot server:
```bash
./mvnw spring-boot:run
```
Or run the packaged standalone jar directly:
```bash
java -jar target/course-registration-waitlist-0.0.1-SNAPSHOT.jar
```
The server binds to port `8080` by default.

### 5.4 Actual Automated Test Results
```text
[INFO] Running com.example.courseregistration.controller.CourseOfferingControllerTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0 -- CourseOfferingController REST API Tests
[INFO] Running com.example.courseregistration.CourseRegistrationApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- Context Loads
[INFO] Running CourseRegistration Concurrency & Race-Condition Invariant Tests
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- Concurrency & Final Seat Contention
[INFO] Running CourseRegistrationService Enrollment Tests
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0 -- Service Layer Invariants & FIFO Promotion
[INFO] Running CourseOffering Domain Invariant Tests
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 -- Domain Invariants & Skip-and-Retain
[INFO] Running com.example.courseregistration.repository.CourseOfferingRepositoryTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- In-Memory Storage Operations
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] Total time: 2.857 s
[INFO] Tests run: 56, Failures: 0, Errors: 0, Skipped: 0
```

---

## 6. REST API Endpoints & Sample Requests

All business logic resides in the domain/service layers. Controllers delegate directly to `CourseRegistrationService`, and [`GlobalExceptionHandler`](file:///Users/devakinandagopal/Desktop/Learning/Projects/CS/src/main/java/com/example/courseregistration/controller/exception/GlobalExceptionHandler.java) formats errors into consistent JSON responses without exposing internal stack traces.

### 6.1 Endpoints Summary

| HTTP Method | Path | Request Body | Status | Description |
| :--- | :--- | :--- | :---: | :--- |
| `POST` | `/api/offerings/{offeringId}/enrollments` | `{"learnerId": "..."}` | `200 OK` | Enrolls learner immediately if capacity remains, or places on FIFO waitlist if full. |
| `DELETE` | `/api/offerings/{offeringId}/enrollments/{learnerId}` | None | `200 OK` | Drops enrolled learner, frees seat, and triggers automated FIFO promotion. |
| `GET` | `/api/offerings/{offeringId}` | None | `200 OK` | Retrieves offering status, capacity, enrolled learners, and waitlist queue positions. |

---

### 6.2 Sample Requests & Responses

#### 1. Enroll Learner (Direct Enrollment)
```bash
curl -X POST http://localhost:8080/api/offerings/CS-201/enrollments \
  -H "Content-Type: application/json" \
  -d '{"learnerId": "learner-101"}'
```
**Response (`200 OK`)**:
```json
{
  "offeringId": "CS-201",
  "learnerId": "learner-101",
  "status": "ENROLLED",
  "waitlistPosition": null,
  "message": "Learner successfully enrolled."
}
```

#### 2. Join FIFO Waitlist (When Full)
```bash
curl -X POST http://localhost:8080/api/offerings/CS-201/enrollments \
  -H "Content-Type: application/json" \
  -d '{"learnerId": "learner-104"}'
```
**Response (`200 OK`)**:
```json
{
  "offeringId": "CS-201",
  "learnerId": "learner-104",
  "status": "WAITLISTED",
  "waitlistPosition": 1,
  "message": "Offering is full; learner placed on waitlist at position 1."
}
```

#### 3. Drop Enrolled Learner with Automated Promotion
```bash
curl -X DELETE http://localhost:8080/api/offerings/CS-201/enrollments/learner-102
```
**Response (`200 OK`)**:
```json
{
  "offeringId": "CS-201",
  "droppedLearnerId": "learner-102",
  "status": "DROPPED",
  "promotedLearnerId": "learner-104",
  "availableCapacity": 0,
  "message": "Learner 'learner-102' dropped. Promoted learner 'learner-104' from waitlist."
}
```

#### 4. Query Offering Status
```bash
curl http://localhost:8080/api/offerings/CS-201
```
**Response (`200 OK`)**:
```json
{
  "offeringId": "CS-201",
  "courseCode": "CS201",
  "title": "Data Structures and Algorithms",
  "capacity": 3,
  "enrolledCount": 3,
  "availableSeats": 0,
  "waitlistCount": 0,
  "prerequisites": ["CS-101"],
  "enrolledLearners": ["learner-101", "learner-103", "learner-104"],
  "waitlist": []
}
```

#### 5. Prerequisite Failure Example
```bash
curl -X POST http://localhost:8080/api/offerings/CS-201/enrollments \
  -H "Content-Type: application/json" \
  -d '{"learnerId": "learner-ineligible"}'
```
**Response (`400 Bad Request`)**:
```json
{
  "timestamp": "2026-10-22T23:20:00.123Z",
  "status": 400,
  "error": "Bad Request",
  "code": "PREREQUISITE_NOT_MET",
  "message": "Learner 'learner-ineligible' does not satisfy required prerequisites: [CS-101]. Completed: []",
  "path": "/api/offerings/CS-201/enrollments"
}
```

#### 6. Duplicate Enrollment Example
```bash
curl -X POST http://localhost:8080/api/offerings/CS-201/enrollments \
  -H "Content-Type: application/json" \
  -d '{"learnerId": "learner-101"}'
```
**Response (`409 Conflict`)**:
```json
{
  "timestamp": "2026-10-22T23:20:01.456Z",
  "status": 409,
  "error": "Conflict",
  "code": "ALREADY_ENROLLED",
  "message": "Learner 'learner-101' is already enrolled in this course offering.",
  "path": "/api/offerings/CS-201/enrollments"
}
```

---

## 7. Project Structure

```text
course-registration-waitlist/
├── .mvn/wrapper/                  # Maven wrapper configuration
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/courseregistration/
│   │   │       ├── CourseRegistrationApplication.java
│   │   │       ├── config/
│   │   │       │   └── DataInitializer.java
│   │   │       ├── controller/
│   │   │       │   ├── CourseOfferingController.java
│   │   │       │   ├── dto/
│   │   │       │   │   ├── DropResponse.java
│   │   │       │   │   ├── EnrollmentRequest.java
│   │   │       │   │   ├── EnrollmentResponse.java
│   │   │       │   │   ├── ErrorResponse.java
│   │   │       │   │   └── OfferingStatusResponse.java
│   │   │       │   └── exception/
│   │   │       │       └── GlobalExceptionHandler.java
│   │   │       ├── domain/
│   │   │       │   ├── CourseOffering.java
│   │   │       │   ├── DropResult.java
│   │   │       │   ├── DropStatus.java
│   │   │       │   ├── EnrollmentResult.java
│   │   │       │   ├── EnrollmentStatus.java
│   │   │       │   ├── Learner.java
│   │   │       │   ├── WaitlistEntry.java
│   │   │       │   └── exception/
│   │   │       │       ├── CourseNotFoundException.java
│   │   │       │       ├── CourseRegistrationDomainException.java
│   │   │       │       ├── DuplicateEnrollmentException.java
│   │   │       │       ├── InvalidCapacityException.java
│   │   │       │       ├── LearnerNotFoundException.java
│   │   │       │       ├── NotEnrolledException.java
│   │   │       │       └── PrerequisiteNotMetException.java
│   │   │       ├── repository/
│   │   │       │   ├── CourseOfferingRepository.java
│   │   │       │   └── LearnerRepository.java
│   │   │       └── service/
│   │   │           └── CourseRegistrationService.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/
│           └── com/example/courseregistration/
│               ├── CourseRegistrationApplicationTests.java
│               ├── concurrency/
│               │   └── CourseRegistrationConcurrencyTest.java
│               ├── controller/
│               │   └── CourseOfferingControllerTest.java
│               ├── domain/
│               │   └── CourseOfferingTest.java
│               ├── repository/
│               │   └── CourseOfferingRepositoryTest.java
│               └── service/
│                   └── CourseRegistrationServiceTest.java
├── .gitignore
├── AI_USAGE.md                    # AI prompt & review tracking log
├── AI_USE_RECORD.md               # Mirrored AI usage record
├── Course-Registration-Rubric-Score.md # Assessment evaluation rubric & criteria checklist
├── DESIGN.md                      # Detailed design specification
├── mvnw
├── mvnw.cmd
├── pom.xml
└── README.md
```

---

## 8. Companion Documentation

- **[DESIGN.md](file:///Users/devakinandagopal/Desktop/Learning/Projects/CS/DESIGN.md)**: Detailed design specification, service boundary, API contracts, state transitions, concurrency model, and architectural trade-offs.
- **[AI_USAGE.md](file:///Users/devakinandagopal/Desktop/Learning/Projects/CS/AI_USAGE.md)** / **[AI_USE_RECORD.md](file:///Users/devakinandagopal/Desktop/Learning/Projects/CS/AI_USE_RECORD.md)**: Complete trace of all prompts, AI suggestions, human review decisions, code changes, and verification evidence across all 8 phases.
- **[Course-Registration-Rubric-Score.md](file:///Users/devakinandagopal/Desktop/Learning/Projects/CS/Course-Registration-Rubric-Score.md)**: Assessment evaluation rubric and comprehensive 13-item acceptance criteria mapping checklist.

---

## 9. Current Limitations & Known Risks

1. **In-Memory Volatility**: Data is stored in-memory; restarting the process clears all course offerings, enrollments, and waitlists. For production, a durable transactional database (e.g. PostgreSQL with row-level locking or optimistic locking) would be required.
2. **Single Offering Scope**: The service slice is bounded to one course offering per assessment instructions. Multi-course dependencies, schedule conflict checking, and multi-term enrollments are intentionally excluded.
3. **Eager Immediate Promotion**: In this bounded slice, waitlist entries do not expire or require periodic confirmation from learners. When a seat opens, the earliest eligible learner is promoted immediately. In enterprise systems, promotions typically issue a time-bounded reservation offer (e.g., 24 hours to claim).
