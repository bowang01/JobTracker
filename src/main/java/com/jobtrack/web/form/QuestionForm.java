package com.jobtrack.web.form;

import com.jobtrack.domain.AnswerRating;
import com.jobtrack.domain.InterviewQuestion;
import com.jobtrack.domain.ProgrammingLanguage;
import com.jobtrack.domain.QuestionCategory;
import com.jobtrack.domain.Visibility;
import com.jobtrack.service.Text;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class QuestionForm {

    @NotNull(message = "Please choose technical or behavioral")
    private QuestionCategory category = QuestionCategory.TECHNICAL;

    /** Only used for technical questions (ignored for behavioral ones); null = not specified. */
    private ProgrammingLanguage language;

    @NotBlank(message = "Please enter the question")
    @Size(max = 5000, message = "Question must be at most 5000 characters")
    private String questionText;

    @Size(max = 10000, message = "Answer must be at most 10000 characters")
    private String answerText;

    /** Optional. */
    private AnswerRating selfRating;

    /** Private by default. The answer and rating are never published. */
    @NotNull(message = "Please choose who can see this question")
    private Visibility visibility = Visibility.PRIVATE;

    public static QuestionForm withCategory(QuestionCategory category) {
        QuestionForm f = new QuestionForm();
        if (category != null) {
            f.category = category;
        }
        return f;
    }

    public static QuestionForm from(InterviewQuestion q) {
        QuestionForm f = new QuestionForm();
        f.category = q.getCategory();
        f.language = q.getLanguage();
        f.questionText = q.getQuestionText();
        f.answerText = q.getAnswerText();
        f.selfRating = q.getSelfRating();
        f.visibility = q.getVisibility();
        return f;
    }

    /** Copies the content fields; visibility is applied by the service (it also sets the publish time). */
    public void applyTo(InterviewQuestion q) {
        q.setCategory(category);
        q.setLanguage(category == QuestionCategory.TECHNICAL ? language : null);
        q.setQuestionText(questionText.trim());
        q.setAnswerText(Text.trimToNull(answerText));
        q.setSelfRating(selfRating);
    }

    public QuestionCategory getCategory() {
        return category;
    }

    public void setCategory(QuestionCategory category) {
        this.category = category;
    }

    public ProgrammingLanguage getLanguage() {
        return language;
    }

    public void setLanguage(ProgrammingLanguage language) {
        this.language = language;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public AnswerRating getSelfRating() {
        return selfRating;
    }

    public void setSelfRating(AnswerRating selfRating) {
        this.selfRating = selfRating;
    }
}
