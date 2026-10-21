package com.jobtrack.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.jobtrack.domain.AnswerRating;
import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.InterviewOutcome;
import com.jobtrack.domain.InterviewType;
import com.jobtrack.domain.ProgrammingLanguage;
import com.jobtrack.domain.QuestionCategory;
import com.jobtrack.domain.User;
import com.jobtrack.domain.Visibility;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.service.InterviewService;
import com.jobtrack.service.JobApplicationService;
import com.jobtrack.service.QuestionService;
import com.jobtrack.service.UserService;
import com.jobtrack.web.form.ApplicationForm;
import com.jobtrack.web.form.InterviewForm;
import com.jobtrack.web.form.ProfileForm;
import com.jobtrack.web.form.QuestionForm;
import com.jobtrack.web.form.RegistrationForm;
import com.jobtrack.web.form.StatusChangeForm;

/**
 * Creates the demo account with realistic sample data: applications in different stages with their status
 * history, past and upcoming interviews, and an interview question bank (some questions public, so the
 * interview hall is not empty for visitors), with technical questions tagged by programming language.
 * <p>Everything goes through the normal services, so the sample data obeys the same business rules as user data.
 * Times are relative to "now", so the dashboard always shows recent activity and upcoming interviews.
 * Company names are fictional.
 */
@Component
public class DemoData {

    /** Used by tests to find the sample data. */
    public static final String SAMPLE_COMPANY = "Harbour Fintech";
    public static final String SAMPLE_PUBLIC_QUESTION = "Explain the N+1 query problem in JPA and how to fix it.";

    private final UserRepository users;
    private final UserService userService;
    private final JobApplicationService applications;
    private final InterviewService interviews;
    private final QuestionService questions;

    public DemoData(UserRepository users, UserService userService, JobApplicationService applications,
                    InterviewService interviews, QuestionService questions) {
        this.users = users;
        this.userService = userService;
        this.applications = applications;
        this.interviews = interviews;
        this.questions = questions;
    }

    /** Creates the demo user with sample data. Does nothing and returns false if the email is already taken. */
    public boolean createIfMissing(String email, String password) {
        if (users.existsByEmailIgnoreCase(UserService.normalizeEmail(email))) {
            return false;
        }
        RegistrationForm reg = new RegistrationForm();
        reg.setEmail(email);
        reg.setPassword(password);
        reg.setConfirmPassword(password);
        User user = userService.register(reg);
        AppUserDetails me = new AppUserDetails(user);

        ProfileForm profile = new ProfileForm();
        profile.setEmail(user.getEmail());
        profile.setDisplayName("Demo User");
        userService.updateProfile(me, profile);

        addApplications(me);
        addQuestions(me);
        return true;
    }

    /**
     * Deletes the demo user with all their applications, documents (and files), interviews and questions,
     * then creates it again with fresh sample data. {@code admin} performs the delete.
     */
    public void reset(String email, String password, AppUserDetails admin) {
        users.findByEmailIgnoreCase(UserService.normalizeEmail(email))
                .filter(u -> !u.isAdmin())
                .ifPresent(u -> userService.deleteUser(u.getId(), admin));
        createIfMissing(email, password);
    }

    // ------------------------------------------------------------------ Applications

    private void addApplications(AppUserDetails me) {
        LocalDateTime now = applications.now();

        // Offer after three interview rounds
        UUID offer = application(me, "Kowhai Software", "Graduate Software Engineer", "Wellington", "SEEK",
                daysAgo(now, 38), ApplicationStatus.APPLIED,
                "Graduate programme for Java and TypeScript developers. Work in a product team on a SaaS platform used by small businesses across New Zealand.",
                "Referred by a friend from the uni coding club.");
        status(me, offer, ApplicationStatus.ASSESSMENT, at(now, 33, 9), "Online coding test (90 minutes)");
        status(me, offer, ApplicationStatus.INTERVIEWING, at(now, 28, 16), "Invited to interviews");
        interview(me, offer, 1, InterviewType.PHONE_SCREEN, at(now, 26, 11), 30, "Talent acquisition partner",
                "Phone", InterviewOutcome.PASSED, "Motivation, visa and availability. Asked why I chose backend development.");
        interview(me, offer, 2, InterviewType.TECHNICAL, at(now, 20, 14), 90, "Two senior engineers", "Video call",
                InterviewOutcome.PASSED, "Live coding: designing a REST endpoint and fixing an N+1 query. Should explain my reasoning out loud earlier.");
        interview(me, offer, 3, InterviewType.FINAL, at(now, 12, 10), 60, "Engineering manager", "Onsite, Wellington office",
                InterviewOutcome.PASSED, "Behavioural questions and a team meet-and-greet.");
        status(me, offer, ApplicationStatus.OFFER, at(now, 4, 15), "Verbal offer, written offer to follow");

        // Interviewing, next round is coming up
        UUID harbour = application(me, SAMPLE_COMPANY, "Junior Java Developer", "Auckland", "LinkedIn",
                daysAgo(now, 18), ApplicationStatus.APPLIED,
                "Spring Boot microservices for online payments. Experience with SQL, REST APIs and Git required; Docker is a plus.",
                null);
        status(me, harbour, ApplicationStatus.INTERVIEWING, at(now, 9, 13), "Recruiter called to book a first interview");
        interview(me, harbour, 1, InterviewType.VIDEO, at(now, 6, 10), 45, "Hiring manager", "Microsoft Teams",
                InterviewOutcome.PASSED, "Walked through my JobTrack project. They liked the access checks and the tests.");
        interview(me, harbour, 2, InterviewType.TECHNICAL, now.plusDays(3).withHour(14).withMinute(0).truncatedTo(ChronoUnit.MINUTES),
                60, "Tech lead", "Microsoft Teams", InterviewOutcome.PENDING, "Prepare: transactions, indexes, Spring Security basics.");

        // Online assessment stage
        UUID assessment = application(me, "Southern Health Tech", "Graduate Developer", "Christchurch", "Company website",
                daysAgo(now, 10), ApplicationStatus.APPLIED, null, null);
        status(me, assessment, ApplicationStatus.ASSESSMENT, at(now, 3, 9), "HackerRank test, due in 5 days");

        // Just applied
        application(me, "Tui Logistics", "Associate Software Developer", "Auckland", "SEEK",
                daysAgo(now, 2), ApplicationStatus.APPLIED,
                "Full-stack role on the warehouse management system (Java, React, PostgreSQL).", null);

        // Rejected after the first interview
        UUID rejected = application(me, "Pohutukawa Games", "Junior Backend Engineer", "Wellington", "LinkedIn",
                daysAgo(now, 30), ApplicationStatus.APPLIED, null, null);
        status(me, rejected, ApplicationStatus.INTERVIEWING, at(now, 22, 10), null);
        interview(me, rejected, 1, InterviewType.VIDEO, at(now, 19, 15), 45, "Backend lead", "Google Meet",
                InterviewOutcome.FAILED, "Struggled with a concurrency question. Review threads, locks and race conditions.");
        status(me, rejected, ApplicationStatus.REJECTED, at(now, 14, 11), "Feedback: strong communication, needs more depth in concurrency");

        // No response
        UUID silent = application(me, "Kauri Cloud", "Graduate DevOps Engineer", "Remote (NZ)", "SEEK",
                daysAgo(now, 45), ApplicationStatus.APPLIED, null, null);
        status(me, silent, ApplicationStatus.NO_RESPONSE, at(now, 15, 9), "No reply after a month");

        // Saved for later
        application(me, "Matai Data", "Junior Data Engineer", "Auckland", "Indeed", null, ApplicationStatus.SAVED,
                "Python and SQL pipelines; applications close at the end of the month.", "Tailor the CV towards SQL and data projects.");
    }

    private UUID application(AppUserDetails me, String company, String position, String location, String source,
                             LocalDate appliedDate, ApplicationStatus initialStatus, String jdText, String notes) {
        ApplicationForm f = new ApplicationForm();
        f.setCompany(company);
        f.setPositionTitle(position);
        f.setLocation(location);
        f.setSource(source);
        f.setAppliedDate(appliedDate);
        f.setInitialStatus(initialStatus);
        f.setJdText(jdText);
        f.setNotes(notes);
        return applications.create(f, me).getId();
    }

    private void status(AppUserDetails me, UUID appId, ApplicationStatus to, LocalDateTime at, String note) {
        StatusChangeForm f = new StatusChangeForm();
        f.setToStatus(to);
        f.setOccurredAt(at);
        f.setNote(note);
        applications.changeStatus(appId, f, me);
    }

    private void interview(AppUserDetails me, UUID appId, int round, InterviewType type, LocalDateTime at, int minutes,
                           String interviewer, String location, InterviewOutcome outcome, String notes) {
        InterviewForm f = InterviewForm.withRound(round);
        f.setInterviewType(type);
        f.setScheduledAt(at);
        f.setDurationMinutes(minutes);
        f.setInterviewer(interviewer);
        f.setLocation(location);
        f.setOutcome(outcome);
        f.setNotes(notes);
        interviews.add(appId, f, me);
    }

    private static LocalDate daysAgo(LocalDateTime now, int days) {
        return now.toLocalDate().minusDays(days);
    }

    /** A time {@code days} days ago (at least 1, so it is never in the future) at the given hour. */
    private static LocalDateTime at(LocalDateTime now, int days, int hour) {
        return now.minusDays(Math.max(days, 1)).withHour(hour).withMinute(0).truncatedTo(ChronoUnit.MINUTES);
    }

    // ------------------------------------------------------------------ Interview questions

    private void addQuestions(AppUserDetails me) {
        QuestionCategory t = QuestionCategory.TECHNICAL;
        QuestionCategory b = QuestionCategory.BEHAVIORAL;
        Visibility pub = Visibility.PUBLIC;
        Visibility priv = Visibility.PRIVATE;

        // Public: shown in the interview hall (only the question text; answers stay private)
        question(me, t, ProgrammingLanguage.JAVA, SAMPLE_PUBLIC_QUESTION,
                "Loading a list of entities and then touching a lazy association on each one runs 1 query for the list plus N queries. "
                        + "Fix with a fetch join, an entity graph or batch fetching, and check the SQL log or a statement counter.",
                AnswerRating.GOOD, pub);
        question(me, t, ProgrammingLanguage.JAVA, "What is the difference between an abstract class and an interface in Java?",
                "An interface defines a contract and supports multiple inheritance of type; since Java 8 it can have default methods. "
                        + "An abstract class can hold state and constructors, but a class can extend only one.",
                AnswerRating.GOOD, pub);
        question(me, t, ProgrammingLanguage.JAVA, "How does a HashMap work internally, and what happens on a hash collision?",
                "Buckets array indexed by the key hash. Collisions go into a linked list that turns into a tree after 8 entries. "
                        + "Resizes when the load factor (0.75) is exceeded. Keys need consistent equals and hashCode.",
                AnswerRating.OK, pub);
        question(me, t, ProgrammingLanguage.JAVA, "What does @Transactional do in Spring, and when does it not take effect?",
                "Wraps the method in a transaction through a proxy. Self-invocation inside the same class bypasses the proxy; "
                        + "by default only unchecked exceptions trigger a rollback.",
                AnswerRating.OK, pub);
        question(me, t, ProgrammingLanguage.SQL, "What is the difference between INNER JOIN and LEFT JOIN? Give an example.", null, null, pub);
        question(me, t, ProgrammingLanguage.SQL, "How do database indexes speed up queries, and what do they cost?", null, null, pub);
        question(me, t, ProgrammingLanguage.GENERAL, "What is the difference between authentication and authorisation?",
                "Authentication: who you are (sign in). Authorisation: what you are allowed to do (roles, ownership checks).",
                AnswerRating.GOOD, pub);
        question(me, t, ProgrammingLanguage.JAVA, "How does a CSRF attack work, and how does Spring Security protect against it?", null, null, pub);
        question(me, t, ProgrammingLanguage.GENERAL, "What is the difference between a process and a thread?", null, null, pub);
        question(me, t, ProgrammingLanguage.GENERAL, "Reverse a singly linked list. What are the time and space complexity?",
                "Iterate with prev, current and next pointers: O(n) time, O(1) space.", AnswerRating.GOOD, pub);
        question(me, t, ProgrammingLanguage.GENERAL, "What happens when you type a URL into the browser and press Enter?", null, null, pub);
        question(me, t, ProgrammingLanguage.GENERAL, "Design a REST API for a to-do list. Which HTTP methods and status codes would you use?", null, null, pub);
        question(me, t, ProgrammingLanguage.GENERAL, "What is Docker, and how is a container different from a virtual machine?", null, null, pub);
        question(me, t, ProgrammingLanguage.CSHARP, "What is the difference between IEnumerable and IQueryable in C#?", null, null, pub);
        question(me, t, ProgrammingLanguage.CSHARP, "Explain async and await in C#. What does ConfigureAwait(false) do?", null, null, pub);
        question(me, t, ProgrammingLanguage.PYTHON, "What is the difference between a list and a tuple in Python?",
                "Lists are mutable, tuples are immutable (and hashable if their items are), so tuples can be dict keys.",
                AnswerRating.GOOD, pub);
        question(me, t, ProgrammingLanguage.PYTHON, "What are Python decorators, and when would you write one?", null, null, pub);
        question(me, t, ProgrammingLanguage.JAVASCRIPT, "Explain the JavaScript event loop. How do promises and setTimeout differ?", null, null, pub);
        question(me, t, ProgrammingLanguage.JAVASCRIPT, "What is the difference between == and === in JavaScript?",
                "== converts types before comparing, === does not. Prefer === to avoid surprises.", AnswerRating.GOOD, pub);
        question(me, b, null, "Tell me about yourself.",
                "Present: final-year software student building full-stack Java projects. Past: what led me to software. "
                        + "Future: why this graduate role is the next step. Keep it under two minutes.",
                AnswerRating.OK, pub);
        question(me, b, null, "Tell me about a time you disagreed with a teammate. How did you handle it?", null, null, pub);
        question(me, b, null, "Describe a project you are proud of and your role in it.",
                "JobTrack: designed the data model and access checks, wrote integration tests, set up CI/CD to a VPS.",
                AnswerRating.GOOD, pub);
        question(me, b, null, "Tell me about a mistake you made and what you learned from it.", null, null, pub);
        question(me, b, null, "How do you prioritise when you have several deadlines at the same time?", null, null, pub);
        question(me, b, null, "Tell me about a time you had to learn a new technology quickly.", null, null, pub);

        // Private: only in the demo user's own question bank
        question(me, t, ProgrammingLanguage.SQL, "Explain optimistic and pessimistic locking. When would you use each?",
                "Optimistic: version column, fail on conflict, good for rare conflicts. Pessimistic: lock rows up front (SELECT ... FOR UPDATE).",
                AnswerRating.POOR, priv);
        question(me, b, null, "Why do you want to work for us?",
                "Research the company first: product, values, tech stack. Connect them to my projects and what I want to learn.",
                AnswerRating.OK, priv);
        question(me, b, null, "Where do you see yourself in five years?", null, null, priv);
    }

    private void question(AppUserDetails me, QuestionCategory category, ProgrammingLanguage language, String text,
                          String answer, AnswerRating rating, Visibility visibility) {
        QuestionForm f = QuestionForm.withCategory(category);
        f.setLanguage(language);
        f.setQuestionText(text);
        f.setAnswerText(answer);
        f.setSelfRating(rating);
        f.setVisibility(visibility);
        questions.create(f, me);
    }
}
