package com.jobtrack.config;

import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.jobtrack.domain.Role;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.service.UserService;

/**
 * Creates the initial administrator from configuration when no admin exists yet.
 * Once an admin exists, the configured password is no longer read (change it on the Profile page instead).
 */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final UserRepository users;
    private final UserService userService;
    private final JobTrackProperties props;

    public AdminInitializer(UserRepository users, UserService userService, JobTrackProperties props) {
        this.users = users;
        this.userService = userService;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.countByRole(Role.ADMIN) > 0) {
            return;
        }
        JobTrackProperties.Admin admin = props.admin();
        if (admin == null || !StringUtils.hasText(admin.email())) {
            log.warn("No administrator exists and jobtrack.admin.email is not set; skipping admin creation");
            return;
        }
        String email = UserService.normalizeEmail(admin.email());
        if (users.existsByEmailIgnoreCase(email)) {
            log.warn("Email {} is already used by a regular user; initial admin not created", email);
            return;
        }
        String password = admin.password();
        boolean generated = !StringUtils.hasText(password);
        if (generated) {
            byte[] buf = new byte[12];
            new SecureRandom().nextBytes(buf);
            password = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        }
        userService.createAdmin(email, password);
        if (generated) {
            log.warn("==== Created initial admin [{}] with random password: {} -- change it after first login, or set JOBTRACK_ADMIN_PASSWORD ====",
                    email, password);
        } else {
            log.info("Created initial admin [{}]", email);
        }
    }
}
