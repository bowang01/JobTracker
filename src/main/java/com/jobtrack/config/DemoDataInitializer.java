package com.jobtrack.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * When the demo account is enabled (jobtrack.demo.enabled): creates it with sample data on startup if it does not
 * exist yet, and resets its sample data on the reset schedule (jobtrack.demo.reset-cron), so whatever visitors
 * changed is gone the next morning (see DemoData.reset: the public sample questions in the hall stay unchanged).
 * Failures are logged and never stop the application.
 */
@Component
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final DemoAccount demo;
    private final DemoData data;

    public DemoDataInitializer(DemoAccount demo, DemoData data) {
        this.demo = demo;
        this.data = data;
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
        try {
            data.reset(demo.getEmail(), demo.getPassword());
            log.info("Reset the demo account [{}]", demo.getEmail());
        } catch (RuntimeException e) {
            log.warn("Could not reset the demo account [{}]", demo.getEmail(), e);
        }
    }
}
