package com.jobtrack.service;

import java.time.LocalDateTime;
import java.util.UUID;

import com.jobtrack.domain.InterviewQuestion;
import com.jobtrack.domain.QuestionCategory;
import com.jobtrack.domain.Visibility;

/**
 * One question as shown in the interview hall. Deliberately has no answer and no self-rating,
 * so templates cannot leak them even by mistake.
 * <p>For regular users only public questions are listed and the owner fields are always null (shared
 * questions are anonymous). For admins every question is listed, with its visibility and owner.
 */
public record PublicQuestion(UUID id,
                             QuestionCategory category,
                             String questionText,
                             LocalDateTime shownDate,
                             boolean mine,
                             Visibility visibility,
                             UUID ownerId,
                             String ownerEmail) {

    /** View for a regular user or a visitor (viewerId null): anonymous. */
    public static PublicQuestion forUser(InterviewQuestion q, UUID viewerId) {
        return new PublicQuestion(q.getId(), q.getCategory(), q.getQuestionText(), q.getPublishedAt(),
                viewerId != null && q.getOwner().getId().equals(viewerId), q.getVisibility(), null, null);
    }

    /** View for an admin: includes private questions, with visibility and owner (still no answer). */
    public static PublicQuestion forAdmin(InterviewQuestion q) {
        return new PublicQuestion(q.getId(), q.getCategory(), q.getQuestionText(),
                q.getPublishedAt() != null ? q.getPublishedAt() : q.getCreatedAt(),
                false, q.getVisibility(), q.getOwner().getId(), q.getOwner().getEmail());
    }

    public boolean isPublic() {
        return visibility == Visibility.PUBLIC;
    }
}
