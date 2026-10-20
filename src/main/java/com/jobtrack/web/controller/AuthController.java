package com.jobtrack.web.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.jobtrack.security.AppUserDetails;
import com.jobtrack.security.LoginSuccessHandler;
import com.jobtrack.service.BusinessException;
import com.jobtrack.service.UserService;
import com.jobtrack.web.form.RegistrationForm;

import jakarta.validation.Valid;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal AppUserDetails me) {
        return "redirect:" + LoginSuccessHandler.homeFor(me);
    }

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal AppUserDetails me) {
        if (me != null) {
            return "redirect:" + LoginSuccessHandler.homeFor(me);
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(@AuthenticationPrincipal AppUserDetails me, Model model) {
        if (me != null) {
            return "redirect:" + LoginSuccessHandler.homeFor(me);
        }
        model.addAttribute("form", new RegistrationForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult br,
                           RedirectAttributes ra) {
        if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword())) {
            br.rejectValue("confirmPassword", "mismatch", "The passwords do not match");
        }
        if (br.hasErrors()) {
            return "auth/register";
        }
        try {
            userService.register(form);
        } catch (BusinessException e) {
            br.rejectValue(e.getField(), "conflict", e.getMessage());
            return "auth/register";
        }
        ra.addFlashAttribute("success", "Registration successful, please sign in");
        return "redirect:/login";
    }
}
