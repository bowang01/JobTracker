package com.jobtrack.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jobtrack.domain.InterviewQuestion;
import com.jobtrack.domain.QuestionCategory;
import com.jobtrack.domain.Visibility;

/**
 * Interview questions. Counts are derived by Spring Data; the dynamic searches live in
 * {@link InterviewQuestionRepositoryCustom} (hand-written implementation).
 */
public interface InterviewQuestionRepository
        extends JpaRepository<InterviewQuestion, UUID>, InterviewQuestionRepositoryCustom {

    long countByOwnerIdAndCategory(UUID ownerId, QuestionCategory category);

    long countByVisibilityAndCategory(Visibility visibility, QuestionCategory category);

    long countByVisibility(Visibility visibility);

    long countByCategory(QuestionCategory category);

    long countByOwnerId(UUID ownerId);
}
