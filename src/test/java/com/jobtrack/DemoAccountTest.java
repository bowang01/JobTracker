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
import com.jobtrack.domain.Role;
import com.jobtrack.domain.User;
import com.jobtrack.security.AppUserDetails;

/** Visitors' landing page and the optional demo account with sample data. */
class DemoAccountTest extends IntegrationTestBase {

    @Autowired
    DemoData demoData;

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

            demoData.reset(email, "demo-password-1", admin);
            User after = userRepository.findByEmailIgnoreCase(email).orElseThrow();
            assertThat(after.getId()).isNotEqualTo(demo.getId());
            mvc.perform(get("/applications").with(user(new AppUserDetails(after))))
                    .andExpect(content().string(containsString(DemoData.SAMPLE_COMPANY)));
        } finally {
            // Tests share one database: remove the demo user so other tests see an unchanged hall
            userRepository.findByEmailIgnoreCase(email).ifPresent(u -> userService.deleteUser(u.getId(), admin));
        }
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
