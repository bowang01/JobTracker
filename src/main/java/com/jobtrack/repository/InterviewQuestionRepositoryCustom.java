package com.jobtrack.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.jobtrack.domain.AnswerRating;
import com.jobtrack.domain.InterviewQuestion;
import com.jobtrack.domain.QuestionCategory;
import com.jobtrack.domain.Visibility;

/**
 * Hand-written dynamic searches for interview questions; implemented in
 * {@code impl.InterviewQuestionRepositoryCustomImpl}. All filter arguments are optional (null = no condition);
 * {@code keyword} is matched case-insensitively as a substring.
 */
public interface InterviewQuestionRepositoryCustom {

    /** One user's own questions; the keyword matches the question or the owner's own answer. Newest first. */
    List<InterviewQuestion> searchOwn(UUID ownerId, QuestionCategory category, AnswerRating rating, String keyword);

    /** Public questions for the interview hall. The keyword matches the question text only, never answers. */
    Page<InterviewQuestion> searchPublic(QuestionCategory category, String keyword, Pageable pageable);

    /**
     * Admin view: every user's questions with the owner loaded. The keyword matches the question text
     * or the owner's email, never answers.
     */
    Page<InterviewQuestion> searchAll(QuestionCategory category, Visibility visibility, UUID ownerId,
                                      String keyword, Pageable pageable);
}
