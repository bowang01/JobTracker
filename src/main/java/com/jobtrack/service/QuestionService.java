package com.jobtrack.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;

import com.jobtrack.domain.AnswerRating;
import com.jobtrack.domain.InterviewQuestion;
import com.jobtrack.domain.QuestionCategory;
import com.jobtrack.domain.Visibility;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.web.form.QuestionForm;

/**
 * The interview question bank: each user's own technical and behavioral questions,
 * independent of applications and interview rounds.
 * <ul>
 *   <li>Private questions (the default): only the owner can see them; anyone else gets 404.</li>
 *   <li>Public questions: listed in the interview hall for every signed-in user, showing only the
 *       question itself (see {@link PublicQuestion}). The answer and self-rating always stay private.</li>
 *   <li>Admins can see every question, public or private, with its owner (in the interview hall and on
 *       the admin Questions page), but never answers or self-ratings.</li>
 *   <li>Only the owner can edit or delete; an admin can only take a public question out of the hall.</li>
 * </ul>
 */
public interface QuestionService {
    int HALL_PAGE_SIZE = 30;
    int ADMIN_PAGE_SIZE = 50;

    record Counts(long technical, long behavioral) {
        public long total() {
            return technical + behavioral;
        }
    }

    List<InterviewQuestion> search(AppUserDetails me, QuestionCategory category, AnswerRating rating, String keyword);

    Counts counts(AppUserDetails me);

    InterviewQuestion create(QuestionForm form, AppUserDetails me);

    InterviewQuestion get(UUID id, AppUserDetails me);

    void update(UUID id, QuestionForm form, AppUserDetails me);

    void delete(UUID id, AppUserDetails me);

    /**
     * The interview hall. Regular users see public questions only, anonymously.
     * Admins see every question from every user, public and private, with its owner (never the answer).
     */
    Page<PublicQuestion> hall(AppUserDetails me, QuestionCategory category, Visibility visibility, String keyword, int page);

    /** Tab counts for the hall: public questions for users, all questions for admins. */
    Counts hallCounts(AppUserDetails me);

    /** Copies a public question into the caller's own bank as a new private question, without any answer. */
    InterviewQuestion copyToMine(UUID publicId, AppUserDetails me);

    /** Admin monitoring: all users' questions, public and private, without answers or self-ratings. */
    Page<AdminQuestion> allForAdmin(AppUserDetails me, QuestionCategory category, Visibility visibility, UUID ownerId, String keyword, int page);

    /** Admin moderation: takes a question out of the hall (makes it private). Never deletes it. */
    void unpublish(UUID publicId, AppUserDetails me);
}
