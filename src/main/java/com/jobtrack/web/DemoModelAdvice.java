package com.jobtrack.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.jobtrack.config.DemoAccount;

/** Makes the demo account available to every page as ${demoAccount} (the sign-in page and the hall show it to visitors). */
@ControllerAdvice
public class DemoModelAdvice {

    private final DemoAccount demo;

    public DemoModelAdvice(DemoAccount demo) {
        this.demo = demo;
    }

    @ModelAttribute("demoAccount")
    public DemoAccount demoAccount() {
        return demo;
    }
}
