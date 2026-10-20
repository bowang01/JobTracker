package com.jobtrack.service;

import java.time.LocalDateTime;
import java.util.UUID;

import com.jobtrack.domain.InterviewQuestion;
import com.jobtrack.domain.QuestionCategory;
import com.jobtrack.domain.Visibility;

/**
 * What an admin sees about any user's question: the question and who owns it.
 * Deliberately has no answer and no self-rating; those stay private to the owner, even from admins.
 */
public record AdminQuestion(UUID id,
                            QuestionCategory category,
                            String questionText,
                            Visibility visibility,
                            UUID ownerId,
                            String ownerEmail,
                            LocalDateTime createdAt,
                            LocalDateTime publishedAt) {

    public static AdminQuestion of(InterviewQuestion q) {
        return new AdminQuestion(q.getId(), q.getCategory(), q.getQuestionText(), q.getVisibility(),
                q.getOwner().getId(), q.getOwner().getEmail(), q.getCreatedAt(), q.getPublishedAt());
    }

    public boolean isPublic() {
        return visibility == Visibility.PUBLIC;
    }
}
