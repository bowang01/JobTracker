package com.jobtrack.web.controller;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.jobtrack.domain.InterviewOutcome;
import com.jobtrack.domain.InterviewType;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.service.BusinessException;
import com.jobtrack.service.InterviewService;
import com.jobtrack.web.form.InterviewForm;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/applications/{appId}/interviews")
public class InterviewController {

    private final InterviewService service;

    public InterviewController(InterviewService service) {
        this.service = service;
    }

    @ModelAttribute
    void referenceData(Model model) {
        model.addAttribute("interviewTypes", InterviewType.values());
        model.addAttribute("outcomes", InterviewOutcome.values());
    }

    @PostMapping
    public String add(@PathVariable UUID appId, @Valid @ModelAttribute("interviewForm") InterviewForm form,
                      BindingResult br, @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        if (!br.hasErrors()) {
            try {
                service.add(appId, form, me);
                ra.addFlashAttribute("success", "Added interview round " + form.getRoundNumber());
                ra.addFlashAttribute("scrollTo", "interviews"); // scroll there without a #fragment in the URL
                return "redirect:/applications/" + appId;
            } catch (BusinessException e) {
                br.rejectValue(e.getField() == null ? "roundNumber" : e.getField(), "business", e.getMessage());
            } catch (DataIntegrityViolationException e) {
                br.rejectValue("roundNumber", "duplicate", "That round number already exists; refresh the page and try again");
            }
        }
        ra.addFlashAttribute("interviewForm", form);
        ra.addFlashAttribute(ApplicationController.BR + "interviewForm", br);
        ra.addFlashAttribute("error", "Interview not saved, please check the form");
        ra.addFlashAttribute("scrollTo", "interviews"); // scroll there without a #fragment in the URL
        return "redirect:/applications/" + appId;
    }

    @GetMapping("/{interviewId}/edit")
    public String editForm(@PathVariable UUID appId, @PathVariable UUID interviewId,
                           @AuthenticationPrincipal AppUserDetails me, Model model) {
        InterviewService.InterviewWithApp iw = service.get(appId, interviewId, me);
        model.addAttribute("app", iw.application());
        model.addAttribute("interview", iw.interview());
        model.addAttribute("interviewForm", InterviewForm.from(iw.interview()));
        return "interviews/edit";
    }

    @PostMapping("/{interviewId}")
    public String update(@PathVariable UUID appId, @PathVariable UUID interviewId,
                         @Valid @ModelAttribute("interviewForm") InterviewForm form, BindingResult br,
                         @AuthenticationPrincipal AppUserDetails me, Model model, RedirectAttributes ra) {
        if (!br.hasErrors()) {
            try {
                service.update(appId, interviewId, form, me);
                ra.addFlashAttribute("success", "Interview updated");
                ra.addFlashAttribute("scrollTo", "interviews"); // scroll there without a #fragment in the URL
                return "redirect:/applications/" + appId;
            } catch (BusinessException e) {
                br.rejectValue(e.getField() == null ? "roundNumber" : e.getField(), "business", e.getMessage());
            } catch (DataIntegrityViolationException e) {
                br.rejectValue("roundNumber", "duplicate", "That round number already exists");
            }
        }
        InterviewService.InterviewWithApp iw = service.get(appId, interviewId, me);
        model.addAttribute("app", iw.application());
        model.addAttribute("interview", iw.interview());
        return "interviews/edit";
    }

    @PostMapping("/{interviewId}/delete")
    public String delete(@PathVariable UUID appId, @PathVariable UUID interviewId,
                         @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        service.delete(appId, interviewId, me);
        ra.addFlashAttribute("success", "Interview deleted");
        ra.addFlashAttribute("scrollTo", "interviews"); // scroll there without a #fragment in the URL
        return "redirect:/applications/" + appId;
    }
}
