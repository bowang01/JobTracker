package com.jobtrack.config;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.jobtrack.domain.Role;
import com.jobtrack.domain.User;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.security.AppUserDetails;

/**
 * When the demo account is enabled (jobtrack.demo.enabled): creates it with sample data on startup if it does not
 * exist yet, and wipes and re-creates it on the reset schedule (jobtrack.demo.reset-cron), so whatever visitors
 * changed is gone the next morning. Failures are logged and never stop the application.
 */
@Component
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final DemoAccount demo;
    private final DemoData data;
    private final UserRepository users;

    public DemoDataInitializer(DemoAccount demo, DemoData data, UserRepository users) {
        this.demo = demo;
        this.data = data;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!demo.isEnabled()) {
            return;
        }
        try {
            if (data.createIfMissing(demo.getEmail(), demo.getPassword())) {
                log.info("Created the demo account [{}] with sample data", demo.getEmail());
            }
        } catch (RuntimeException e) {
            log.warn("Could not create the demo account [{}]", demo.getEmail(), e);
        }
    }

    @Scheduled(cron = "${jobtrack.demo.reset-cron:-}", zone = "${jobtrack.demo.reset-zone:Pacific/Auckland}")
    public void scheduledReset() {
        if (!demo.isEnabled()) {
            return;
        }
        Optional<User> admin = users.findByRoleOrderByEmailAsc(Role.ADMIN).stream().findFirst();
        if (admin.isEmpty()) {
            log.warn("Demo reset skipped: no administrator exists to perform it");
            return;
        }
        try {
            data.reset(demo.getEmail(), demo.getPassword(), new AppUserDetails(admin.get()));
            log.info("Reset the demo account [{}]", demo.getEmail());
        } catch (RuntimeException e) {
            log.warn("Could not reset the demo account [{}]", demo.getEmail(), e);
        }
    }
}
