package com.jobtrack.web.state;

import java.io.Serializable;

import com.jobtrack.domain.QuestionCategory;
import com.jobtrack.domain.Visibility;

/** Filter state of the interview hall (kept in the session). {@code visibility} is only used for admins. */
public class HallFilter implements Serializable {

    private QuestionCategory category;
    private Visibility visibility;
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
