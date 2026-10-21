package com.jobtrack.web.state;

import java.io.Serializable;

import com.jobtrack.domain.AnswerRating;
import com.jobtrack.domain.ProgrammingLanguage;
import com.jobtrack.domain.QuestionCategory;

/** Filter state of "My questions" (kept in the session). */
public class QuestionFilter implements Serializable {

    private QuestionCategory category;
    /** Technical questions about this language only; null = any. */
    private ProgrammingLanguage language;
    private AnswerRating rating;
    private String q;

    public boolean isFiltered() {
        return rating != null || language != null || (q != null && !q.isBlank());
    }

    public ProgrammingLanguage getLanguage() {
        return language;
    }

    public void setLanguage(ProgrammingLanguage language) {
        this.language = language;
    }

    public QuestionCategory getCategory() {
        return category;
    }

    public void setCategory(QuestionCategory category) {
        this.category = category;
    }

    public AnswerRating getRating() {
        return rating;
    }

    public void setRating(AnswerRating rating) {
        this.rating = rating;
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }
}
