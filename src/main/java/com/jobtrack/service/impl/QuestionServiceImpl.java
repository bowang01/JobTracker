package com.jobtrack.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobtrack.domain.AnswerRating;
import com.jobtrack.domain.InterviewQuestion;
import com.jobtrack.domain.ProgrammingLanguage;
import com.jobtrack.domain.QuestionCategory;
import com.jobtrack.domain.Visibility;
import com.jobtrack.repository.InterviewQuestionRepository;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.web.form.QuestionForm;

import com.jobtrack.service.AdminQuestion;
import com.jobtrack.service.BusinessException;
import com.jobtrack.service.NotFoundException;
import com.jobtrack.service.PublicQuestion;
import com.jobtrack.service.QuestionService;
import com.jobtrack.service.Text;

/**
 * The interview question bank: each user's own technical and behavioral questions,
 * independent of applications and interview rounds.
 * <ul>
 *   <li>Private questions (the default): only the owner can see them; anyone else gets 404.</li>
 *   <li>Public questions: listed in the interview hall for everyone, including visitors who are not signed in, showing only the
 *       question itself (see {@link PublicQuestion}). The answer and self-rating always stay private.</li>
 *   <li>Admins can see every question, public or private, with its owner (in the interview hall and on
 *       the admin Questions page), but never answers or self-ratings.</li>
 *   <li>Only the owner can edit or delete; an admin can only take a public question out of the hall.</li>
 * </ul>
 */
@Service
public class QuestionServiceImpl implements QuestionService {

    private final InterviewQuestionRepository questions;
    private final UserRepository users;
    private final Clock clock;

    public QuestionServiceImpl(InterviewQuestionRepository questions, UserRepository users, Clock clock) {
        this.questions = questions;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public List<InterviewQuestion> search(AppUserDetails me, QuestionCategory category, ProgrammingLanguage language,
                                          AnswerRating rating, String keyword) {
        return questions.searchOwn(me.getId(), category, languageFor(category, language), rating, keyword);
    }

    /** Behavioral questions have no language, so a language filter only applies when technical questions are listed. */
    private static ProgrammingLanguage languageFor(QuestionCategory category, ProgrammingLanguage language) {
        return category == QuestionCategory.BEHAVIORAL ? null : language;
    }

    @Transactional(readOnly = true)
    @Override
    public Counts counts(AppUserDetails me) {
        return new Counts(questions.countByOwnerIdAndCategory(me.getId(), QuestionCategory.TECHNICAL),
                questions.countByOwnerIdAndCategory(me.getId(), QuestionCategory.BEHAVIORAL));
    }

    @Transactional
    @Override
    public InterviewQuestion create(QuestionForm form, AppUserDetails me) {
        if (me.isAdmin()) {
            throw new AccessDeniedException("Administrators do not have a question bank");
        }
        InterviewQuestion q = new InterviewQuestion();
        q.setOwner(users.getReferenceById(me.getId()));
        form.applyTo(q);
        q.changeVisibility(form.getVisibility(), LocalDateTime.now(clock));
        return questions.save(q);
    }

    @Transactional(readOnly = true)
    @Override
    public InterviewQuestion get(UUID id, AppUserDetails me) {
        return loadOwned(id, me);
    }

    @Transactional
    @Override
    public void update(UUID id, QuestionForm form, AppUserDetails me) {
        InterviewQuestion q = loadOwned(id, me);
        form.applyTo(q);
        q.changeVisibility(form.getVisibility(), LocalDateTime.now(clock));
    }

    @Transactional
    @Override
    public void delete(UUID id, AppUserDetails me) {
        questions.delete(loadOwned(id, me));
    }

    // ------------------------------------------------------------------ Interview hall

    /**
     * The interview hall. Visitors (me == null) and regular users see public questions only, anonymously.
     * Admins see every question from every user, public and private, with its owner (never the answer).
     */
    @Transactional(readOnly = true)
    @Override
    public Page<PublicQuestion> hall(AppUserDetails me, QuestionCategory category, ProgrammingLanguage language,
                                     Visibility visibility, String keyword, int page) {
        ProgrammingLanguage lang = languageFor(category, language);
        if (me != null && me.isAdmin()) {
            return questions.searchAll(category, lang, visibility, null, keyword,
                            PageRequest.of(Math.max(page, 0), HALL_PAGE_SIZE,
                                    Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))))
                    .map(PublicQuestion::forAdmin);
        }
        return questions.searchPublic(category, lang, keyword,
                        PageRequest.of(Math.max(page, 0), HALL_PAGE_SIZE,
                                Sort.by(Sort.Direction.DESC, "publishedAt").and(Sort.by(Sort.Direction.DESC, "id"))))
                .map(q -> PublicQuestion.forUser(q, me == null ? null : me.getId()));
    }

    /** Tab counts for the hall: public questions for visitors and users, all questions for admins. */
    @Transactional(readOnly = true)
    @Override
    public Counts hallCounts(AppUserDetails me) {
        if (me != null && me.isAdmin()) {
            return new Counts(questions.countByCategory(QuestionCategory.TECHNICAL),
                    questions.countByCategory(QuestionCategory.BEHAVIORAL));
        }
        return new Counts(questions.countByVisibilityAndCategory(Visibility.PUBLIC, QuestionCategory.TECHNICAL),
                questions.countByVisibilityAndCategory(Visibility.PUBLIC, QuestionCategory.BEHAVIORAL));
    }

    /** Copies a public question into the caller's own bank as a new private question, without any answer. */
    @Transactional
    @Override
    public InterviewQuestion copyToMine(UUID publicId, AppUserDetails me) {
        if (me.isAdmin()) {
            throw new AccessDeniedException("Administrators do not have a question bank");
        }
        InterviewQuestion src = loadPublic(publicId);
        if (src.getOwner().getId().equals(me.getId())) {
            throw new BusinessException("This question is already in your bank");
        }
        InterviewQuestion copy = new InterviewQuestion();
        copy.setOwner(users.getReferenceById(me.getId()));
        copy.setCategory(src.getCategory());
        copy.setLanguage(src.getLanguage());
        copy.setQuestionText(src.getQuestionText());
        copy.changeVisibility(Visibility.PRIVATE, LocalDateTime.now(clock));
        return questions.save(copy);
    }

    /** Admin monitoring: all users' questions, public and private, without answers or self-ratings. */
    @Transactional(readOnly = true)
    @Override
    public Page<AdminQuestion> allForAdmin(AppUserDetails me, QuestionCategory category, ProgrammingLanguage language,
                                           Visibility visibility, UUID ownerId, String keyword, int page) {
        if (!me.isAdmin()) {
            throw new AccessDeniedException("Only administrators can see all questions");
        }
        return questions.searchAll(category, languageFor(category, language), visibility, ownerId, keyword,
                        PageRequest.of(Math.max(page, 0), ADMIN_PAGE_SIZE,
                                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))))
                .map(AdminQuestion::of);
    }

    /** Admin moderation: takes a question out of the hall (makes it private). Never deletes it. */
    @Transactional
    @Override
    public void unpublish(UUID publicId, AppUserDetails me) {
        if (!me.isAdmin()) {
            throw new AccessDeniedException("Only administrators can moderate the interview hall");
        }
        loadPublic(publicId).changeVisibility(Visibility.PRIVATE, LocalDateTime.now(clock));
    }

    private InterviewQuestion loadPublic(UUID id) {
        InterviewQuestion q = id == null ? null : questions.findById(id).orElse(null);
        if (q == null || !q.isPublic()) {
            throw new NotFoundException(); // private questions are invisible to everyone but the owner
        }
        return q;
    }

    private InterviewQuestion loadOwned(UUID id, AppUserDetails me) {
        if (id == null || me == null) {
            throw new NotFoundException();
        }
        InterviewQuestion q = questions.findById(id).orElseThrow(NotFoundException::new);
        if (!q.getOwner().getId().equals(me.getId())) {
            throw new NotFoundException();
        }
        return q;
    }
}
