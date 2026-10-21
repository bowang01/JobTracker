package com.jobtrack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.jobtrack.config.DemoAccount;
import com.jobtrack.config.DemoData;
import com.jobtrack.config.JobTrackProperties;
import com.jobtrack.domain.InterviewQuestion;
import com.jobtrack.domain.QuestionCategory;
import com.jobtrack.domain.Role;
import com.jobtrack.domain.User;
import com.jobtrack.repository.InterviewQuestionRepository;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.service.QuestionService;
import com.jobtrack.web.form.QuestionForm;

/** Visitors' landing page and the optional demo account with sample data. */
class DemoAccountTest extends IntegrationTestBase {

    @Autowired
    DemoData demoData;

    @Autowired
    QuestionService questionService;

    @Autowired
    InterviewQuestionRepository questions;

    @Test
    void visitorsLandOnTheInterviewHall_withAnIntroduction() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/hall"));
        mvc.perform(get("/hall"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your job search")))
                // The demo account is off in tests, so there is no demo button
                .andExpect(content().string(not(containsString("Try the demo"))));

        // Signed-in users still go to their own home page
        AppUserDetails me = newUser("landing");
        mvc.perform(get("/").with(user(me))).andExpect(redirectedUrl("/dashboard"));
        mvc.perform(get("/hall").with(user(me))).andExpect(content().string(not(containsString("Your job search"))));
    }

    @Test
    void demoDataIsCreatedOnce_andCanBeReset() throws Exception {
        String email = "demo_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        AppUserDetails admin = newAdmin();
        try {
            assertThat(demoData.createIfMissing(email, "demo-password-1")).isTrue();
            assertThat(demoData.createIfMissing(email, "demo-password-1")).isFalse();

            AppUserDetails demo = new AppUserDetails(userRepository.findByEmailIgnoreCase(email).orElseThrow());
            mvc.perform(get("/dashboard").with(user(demo))).andExpect(status().isOk());
            mvc.perform(get("/applications").with(user(demo)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(DemoData.SAMPLE_COMPANY)));
            // Public sample questions fill the hall for visitors
            mvc.perform(get("/hall"))
                    .andExpect(content().string(containsString(DemoData.SAMPLE_PUBLIC_QUESTION)));

            // Visitors change things: delete all applications and one public sample question, add a private one
            InterviewQuestion sample = sampleQuestion(demo);
            long publicBefore = publicQuestionsOf(demo);
            userService.deleteApplicationData(demo.getId());
            questions.delete(sample);
            QuestionForm junk = QuestionForm.withCategory(QuestionCategory.TECHNICAL);
            junk.setQuestionText("Visitor junk " + UUID.randomUUID());
            InterviewQuestion junkQuestion = questionService.create(junk, demo);
            InterviewQuestion keptPublic = questionService.search(demo, null, null, null, null).stream()
                    .filter(q -> q.isPublic()).findFirst().orElseThrow();

            demoData.reset(email, "demo-password-1");

            // Same account; applications back; untouched public samples keep their id and share date
            User after = userRepository.findByEmailIgnoreCase(email).orElseThrow();
            assertThat(after.getId()).isEqualTo(demo.getId());
            mvc.perform(get("/applications").with(user(demo)))
                    .andExpect(content().string(containsString(DemoData.SAMPLE_COMPANY)));
            InterviewQuestion keptAfter = questions.findById(keptPublic.getId()).orElseThrow();
            assertThat(keptAfter.getPublishedAt()).isEqualTo(keptPublic.getPublishedAt());
            // The deleted sample is back, the visitor's question is gone, and nothing is duplicated
            assertThat(sampleQuestion(demo).getId()).isNotEqualTo(sample.getId());
            assertThat(questions.findById(junkQuestion.getId())).isEmpty();
            assertThat(publicQuestionsOf(demo)).isEqualTo(publicBefore);
        } finally {
            // Tests share one database: remove the demo user so other tests see an unchanged hall
            userRepository.findByEmailIgnoreCase(email).ifPresent(u -> userService.deleteUser(u.getId(), admin));
        }
    }

    private InterviewQuestion sampleQuestion(AppUserDetails demo) {
        return questionService.search(demo, null, null, null, null).stream()
                .filter(q -> q.getQuestionText().equals(DemoData.SAMPLE_PUBLIC_QUESTION)).findFirst().orElseThrow();
    }

    private long publicQuestionsOf(AppUserDetails demo) {
        return questionService.search(demo, null, null, null, null).stream().filter(q -> q.isPublic()).count();
    }

    @Test
    void onlyTheConfiguredRegularUserIsTheDemoAccount() {
        DemoAccount demo = new DemoAccount(new JobTrackProperties(null, null, null,
                new JobTrackProperties.Demo(true, " Demo@Example.com ", "demo1234", "-")));
        assertThat(demo.isEnabled()).isTrue();
        assertThat(demo.isResetScheduled()).isFalse();
        assertThat(demo.isDemo(principal("demo@example.com", Role.USER))).isTrue();
        assertThat(demo.isDemo(principal("someone@example.com", Role.USER))).isFalse();
        assertThat(demo.isDemo(principal("demo@example.com", Role.ADMIN))).isFalse();
        assertThat(demo.isDemo(null)).isFalse();

        DemoAccount off = new DemoAccount(new JobTrackProperties(null, null, null,
                new JobTrackProperties.Demo(false, "demo@example.com", "demo1234", "0 0 4 * * *")));
        assertThat(off.isEnabled()).isFalse();
        assertThat(off.isDemo(principal("demo@example.com", Role.USER))).isFalse();
    }

    private static AppUserDetails principal(String email, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setRole(role);
        u.setPasswordHash("x");
        u.setEnabled(true);
        return new AppUserDetails(u);
    }
}
