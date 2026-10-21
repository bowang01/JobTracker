package com.jobtrack.config;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.jobtrack.security.AppUserDetails;
import com.jobtrack.service.UserService;

/**
 * The optional public demo account: a regular user whose email and password are shown to visitors,
 * so recruiters can look around without registering.
 * <p>Because everyone shares it, the demo account cannot change its email or password, and cannot share
 * questions to the public interview hall (see ProfileController and QuestionController).
 * Its data is wiped and re-created on the reset schedule (DemoDataInitializer).
 */
@Component
public class DemoAccount {

    private final boolean enabled;
    private final String email;
    private final String password;
    private final boolean resetScheduled;

    public DemoAccount(JobTrackProperties props) {
        JobTrackProperties.Demo demo = props.demo();
        this.enabled = demo != null && demo.enabled()
                && StringUtils.hasText(demo.email()) && StringUtils.hasText(demo.password());
        this.email = enabled ? UserService.normalizeEmail(demo.email()) : null;
        this.password = enabled ? demo.password() : null;
        this.resetScheduled = enabled && StringUtils.hasText(demo.resetCron()) && !"-".equals(demo.resetCron().trim());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    /** True when the demo data is wiped and re-created on a schedule (shown to visitors). */
    public boolean isResetScheduled() {
        return resetScheduled;
    }

    /** Whether the signed-in user is the shared demo account. */
    public boolean isDemo(AppUserDetails me) {
        return enabled && me != null && !me.isAdmin() && email.equalsIgnoreCase(me.getUsername());
    }
}
