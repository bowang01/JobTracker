package com.jobtrack.web.state;

import java.io.Serializable;
import java.util.UUID;

import com.jobtrack.domain.QuestionCategory;
import com.jobtrack.domain.Visibility;

/** Filter state of the admin "all questions" page (kept in the session). */
public class AdminQuestionFilter implements Serializable {

    private QuestionCategory category;
    private Visibility visibility;
    private UUID ownerId;
    private String q;
    private int page;

    public QuestionCategory getCategory() {
        return category;
    }

    public void setCategory(QuestionCategory category) {
        this.category = category;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(page, 0);
    }
}
