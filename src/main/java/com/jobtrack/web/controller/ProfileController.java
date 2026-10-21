package com.jobtrack.web.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.jobtrack.config.DemoAccount;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.service.BusinessException;
import com.jobtrack.service.UserService;
import com.jobtrack.web.form.PasswordChangeForm;
import com.jobtrack.web.form.ProfileForm;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    /** Everyone shares the demo account, so nobody may change its email or password and lock the others out. */
    static final String DEMO_LOCKED = "The demo account's email and password cannot be changed. Create your own account to try this.";

    private final UserService userService;
    private final DemoAccount demo;

    public ProfileController(UserService userService, DemoAccount demo) {
        this.userService = userService;
        this.demo = demo;
    }

    @GetMapping
    public String profile(@AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("user", userService.get(me.getId()));
        if (!model.containsAttribute("profileForm")) {
            model.addAttribute("profileForm", ProfileForm.from(userService.get(me.getId())));
        }
        if (!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm", new PasswordChangeForm());
        }
        return "profile/profile";
    }

    @PostMapping
    public String updateProfile(@Valid @ModelAttribute("profileForm") ProfileForm form, BindingResult br,
                                @AuthenticationPrincipal AppUserDetails me, Model model, RedirectAttributes ra) {
        if (demo.isDemo(me)) {
            ra.addFlashAttribute("error", DEMO_LOCKED);
            return "redirect:/profile";
        }
        if (!br.hasErrors()) {
            try {
                userService.updateProfile(me, form);
                ra.addFlashAttribute("success", "Profile updated");
                return "redirect:/profile";
            } catch (BusinessException e) {
                br.rejectValue(e.getField(), "business", e.getMessage());
            }
        }
        model.addAttribute("user", userService.get(me.getId()));
        model.addAttribute("passwordForm", new PasswordChangeForm());
        return "profile/profile";
    }

    @PostMapping("/password")
    public String changePassword(@Valid @ModelAttribute("passwordForm") PasswordChangeForm form, BindingResult br,
                                 @AuthenticationPrincipal AppUserDetails me, Model model, RedirectAttributes ra) {
        if (demo.isDemo(me)) {
            ra.addFlashAttribute("error", DEMO_LOCKED);
            return "redirect:/profile";
        }
        if (!br.hasErrors()) {
            try {
                userService.changePassword(me, form);
                ra.addFlashAttribute("success", "Password changed");
                return "redirect:/profile";
            } catch (BusinessException e) {
                br.rejectValue(e.getField(), "business", e.getMessage());
            }
        }
        model.addAttribute("user", userService.get(me.getId()));
        model.addAttribute("profileForm", ProfileForm.from(userService.get(me.getId())));
        return "profile/profile";
    }
}
