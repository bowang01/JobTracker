package com.jobtrack.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.jobtrack.domain.AnswerRating;
import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.InterviewOutcome;
import com.jobtrack.domain.InterviewQuestion;
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
 * Creates the demo account with a small set of realistic sample data: 4 applications in different stages with
 * their status history and past and upcoming interviews, and 5 interview questions (3 public, so the interview hall
 * is not empty for visitors; technical ones tagged by programming language).
 * <p>Everything goes through the normal services, so the sample data obeys the same business rules as user data.
 * The public sample questions are published once and kept across resets (see {@link #reset}).
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
        addQuestions(me, Set.of());
        return true;
    }

    /**
     * Resets the demo account to the sample data, keeping the account itself.
     * <ul>
     *   <li>Applications, status history, interviews and documents (and their files) are deleted and re-created.</li>
     *   <li>The public sample questions are kept as they are, so their ids and share dates in the interview hall
     *       never change and questions real users share later stay above them. The demo account cannot edit or
     *       publish public questions, so they are still the original text.</li>
     *   <li>Every other question (private ones, and public ones a visitor deleted or made private) is removed,
     *       and missing sample questions are added again.</li>
     * </ul>
     * Creates the account if it does not exist.
     */
    public void reset(String email, String password) {
        User user = users.findByEmailIgnoreCase(UserService.normalizeEmail(email))
                .filter(u -> !u.isAdmin())
                .orElse(null);
        if (user == null) {
            createIfMissing(email, password);
            return;
        }
        AppUserDetails me = new AppUserDetails(user);
        userService.deleteApplicationData(user.getId());
        addApplications(me);

        Set<String> publicSamples = new HashSet<>();
        for (Seed q : QUESTIONS) {
            if (q.isPublic()) {
                publicSamples.add(q.text());
            }
        }
        Set<String> kept = new HashSet<>();
        for (InterviewQuestion q : questions.search(me, null, null, null, null)) {
            boolean untouchedSample = q.isPublic() && publicSamples.contains(q.getQuestionText())
                    && kept.add(q.getQuestionText()); // a duplicate text is removed
            if (!untouchedSample) {
                questions.delete(q.getId(), me);
            }
        }
        addQuestions(me, kept);
    }

    // ------------------------------------------------------------------ Applications

    private void addApplications(AppUserDetails me) {
        LocalDateTime now = applications.now();

        // Offer after two interview rounds
        UUID offer = application(me, "Kowhai Software", "Graduate Software Engineer", "Wellington", "SEEK",
                daysAgo(now, 30), ApplicationStatus.APPLIED,
                "Graduate programme for Java and TypeScript developers. Work in a product team on a SaaS platform used by small businesses across New Zealand.",
                "Referred by a friend from the uni coding club.");
        status(me, offer, ApplicationStatus.INTERVIEWING, at(now, 22, 16), "Invited to interviews");
        interview(me, offer, 1, InterviewType.TECHNICAL, at(now, 18, 14), 90, "Two senior engineers", "Video call",
                InterviewOutcome.PASSED, "Live coding: designing a REST endpoint and fixing an N+1 query. Should explain my reasoning out loud earlier.");
        interview(me, offer, 2, InterviewType.FINAL, at(now, 10, 10), 60, "Engineering manager", "Onsite, Wellington office",
                InterviewOutcome.PASSED, "Behavioural questions and a team meet-and-greet.");
        status(me, offer, ApplicationStatus.OFFER, at(now, 4, 15), "Verbal offer, written offer to follow");

        // Interviewing, next round is coming up
        UUID harbour = application(me, SAMPLE_COMPANY, "Junior Java Developer", "Auckland", "LinkedIn",
                daysAgo(now, 14), ApplicationStatus.APPLIED,
                "Spring Boot microservices for online payments. Experience with SQL, REST APIs and Git required; Docker is a plus.",
                null);
        status(me, harbour, ApplicationStatus.INTERVIEWING, at(now, 7, 13), "Recruiter called to book a first interview");
        interview(me, harbour, 1, InterviewType.VIDEO, at(now, 5, 10), 45, "Hiring manager", "Microsoft Teams",
                InterviewOutcome.PASSED, "Walked through my JobTrack project. They liked the access checks and the tests.");
        interview(me, harbour, 2, InterviewType.TECHNICAL, now.plusDays(3).withHour(14).withMinute(0).truncatedTo(ChronoUnit.MINUTES),
                60, "Tech lead", "Microsoft Teams", InterviewOutcome.PENDING, "Prepare: transactions, indexes, Spring Security basics.");

        // Rejected after the first interview
        UUID rejected = application(me, "Pohutukawa Games", "Junior Backend Engineer", "Wellington", "LinkedIn",
                daysAgo(now, 25), ApplicationStatus.APPLIED, null, null);
        status(me, rejected, ApplicationStatus.INTERVIEWING, at(now, 20, 10), null);
        interview(me, rejected, 1, InterviewType.VIDEO, at(now, 17, 15), 45, "Backend lead", "Google Meet",
                InterviewOutcome.FAILED, "Struggled with a concurrency question. Review threads, locks and race conditions.");
        status(me, rejected, ApplicationStatus.REJECTED, at(now, 12, 11), "Feedback: strong communication, needs more depth in concurrency");

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

    private static final QuestionCategory T = QuestionCategory.TECHNICAL;
    private static final QuestionCategory B = QuestionCategory.BEHAVIORAL;
    private static final Visibility PUB = Visibility.PUBLIC;
    private static final Visibility PRIV = Visibility.PRIVATE;

    /** One sample question of the demo account's question bank. */
    private record Seed(QuestionCategory category, ProgrammingLanguage language, String text, String answer,
                        AnswerRating rating, Visibility visibility) {

        boolean isPublic() {
            return visibility == Visibility.PUBLIC;
        }
    }

    /** The demo account's question bank. Public ones fill the interview hall (question text only). */
    private static final List<Seed> QUESTIONS = List.of(
            // Public: shown in the interview hall (only the question text; answers stay private).
            // Kept to a few so the sample questions do not crowd out the ones real users share.
            new Seed(T, ProgrammingLanguage.JAVA, SAMPLE_PUBLIC_QUESTION,
                    "Loading a list of entities and then touching a lazy association on each one runs 1 query for the list plus N queries. "
                            + "Fix with a fetch join, an entity graph or batch fetching, and check the SQL log or a statement counter.",
                    AnswerRating.GOOD, PUB),
            new Seed(T, ProgrammingLanguage.PYTHON, "What is the difference between a list and a tuple in Python?",
                    "Lists are mutable, tuples are immutable (and hashable if their items are), so tuples can be dict keys.",
                    AnswerRating.GOOD, PUB),
            new Seed(B, null, "Tell me about a time you disagreed with a teammate. How did you handle it?", null, null, PUB),

            // Private: only in the demo user's own question bank
            new Seed(T, ProgrammingLanguage.SQL, "Explain optimistic and pessimistic locking. When would you use each?",
                    "Optimistic: version column, fail on conflict, good for rare conflicts. Pessimistic: lock rows up front (SELECT ... FOR UPDATE).",
                    AnswerRating.POOR, PRIV),
            new Seed(B, null, "Tell me about yourself.",
                    "Present: final-year software student building full-stack Java projects. Past: what led me to software. "
                            + "Future: why this graduate role is the next step. Keep it under two minutes.",
                    AnswerRating.OK, PRIV)
    );

    /** Adds the sample questions, except public ones whose text is in {@code keep} (they are still there). */
    private void addQuestions(AppUserDetails me, Set<String> keep) {
        for (Seed q : QUESTIONS) {
            if (q.isPublic() && keep.contains(q.text())) {
                continue;
            }
            QuestionForm f = QuestionForm.withCategory(q.category());
            f.setLanguage(q.language());
            f.setQuestionText(q.text());
            f.setAnswerText(q.answer());
            f.setSelfRating(q.rating());
            f.setVisibility(q.visibility());
            questions.create(f, me);
        }
    }
}
