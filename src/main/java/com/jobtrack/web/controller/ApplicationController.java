package com.jobtrack.web.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.DocumentType;
import com.jobtrack.domain.InterviewOutcome;
import com.jobtrack.domain.InterviewType;
import com.jobtrack.domain.JobApplication;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.service.ApplicationDetail;
import com.jobtrack.service.AllowedFileType;
import com.jobtrack.service.BusinessException;
import com.jobtrack.service.FileStorageService;
import com.jobtrack.service.JobApplicationService;
import com.jobtrack.service.UserService;
import com.jobtrack.web.form.ApplicationForm;
import com.jobtrack.web.form.ApplicationSearchCriteria;
import com.jobtrack.web.form.InterviewForm;
import com.jobtrack.web.form.StatusChangeForm;

import com.jobtrack.web.state.SessionState;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/applications")
public class ApplicationController {

    static final String BR = BindingResult.MODEL_KEY_PREFIX;

    private final JobApplicationService service;
    private final UserService userService;
    private final FileStorageService storage;

    public ApplicationController(JobApplicationService service, UserService userService, FileStorageService storage) {
        this.service = service;
        this.userService = userService;
        this.storage = storage;
    }

    @ModelAttribute
    void referenceData(Model model) {
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("interviewTypes", InterviewType.values());
        model.addAttribute("outcomes", InterviewOutcome.values());
        model.addAttribute("documentTypes", DocumentType.values());
    }

    // ------------------------------------------------------------------ List

    /**
     * The list always lives at the plain URL /applications. Its search/filter/sort/page state is kept in the
     * session and changed through the POST endpoints below (Post/Redirect/Get), so no query string is shown.
     */
    @GetMapping
    public String list(HttpSession session, @AuthenticationPrincipal AppUserDetails me, Model model) {
        ApplicationSearchCriteria criteria = criteria(session);
        model.addAttribute("criteria", criteria);
        model.addAttribute("page", service.search(criteria, me, criteria.getPage()));
        if (me.isAdmin()) {
            model.addAttribute("users", userService.listRegularUsers());
        }
        return "applications/list";
    }

    /** Applies the filter form (also used by "show applications with status X" links elsewhere). */
    @PostMapping("/filter")
    public String filter(@ModelAttribute("submitted") ApplicationSearchCriteria submitted, BindingResult ignored,
                         HttpSession session) {
        // ignored: invalid values (e.g. an unknown status) simply become "no filter" instead of an error
        criteria(session).applyFilters(submitted);
        return "redirect:/applications";
    }

    @PostMapping("/sort")
    public String sort(@RequestParam String key, HttpSession session) {
        criteria(session).toggleSort(key);
        return "redirect:/applications";
    }

    @PostMapping("/page")
    public String page(@RequestParam(defaultValue = "0") int page, HttpSession session) {
        criteria(session).setPage(page);
        return "redirect:/applications";
    }

    @PostMapping("/reset")
    public String reset(HttpSession session) {
        SessionState.clear(session, SessionState.APPLICATIONS);
        return "redirect:/applications";
    }

    private static ApplicationSearchCriteria criteria(HttpSession session) {
        return SessionState.get(session, SessionState.APPLICATIONS, ApplicationSearchCriteria::new);
    }

    // ------------------------------------------------------------------ Create

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new ApplicationForm());
        model.addAttribute("editing", false);
        return "applications/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ApplicationForm form, BindingResult br,
                         @AuthenticationPrincipal AppUserDetails me, Model model, RedirectAttributes ra) {
        if (br.hasErrors()) {
            model.addAttribute("editing", false);
            return "applications/form";
        }
        JobApplication app = service.create(form, me);
        ra.addFlashAttribute("success", "Application created. You can upload your CV, cover letter and JD file below.");
        return "redirect:/applications/" + app.getId();
    }

    // ------------------------------------------------------------------ Detail

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails me, Model model) {
        ApplicationDetail d = service.getDetail(id, me);
        model.addAttribute("d", d);
        model.addAttribute("app", d.application());
        if (!model.containsAttribute("statusForm")) {
            model.addAttribute("statusForm", StatusChangeForm.defaults(service.now()));
        }
        // The interview form is only in the model after a failed submission (flash attribute); open it in that case
        model.addAttribute("interviewFormOpen", model.containsAttribute("interviewForm"));
        if (!model.containsAttribute("interviewForm")) {
            model.addAttribute("interviewForm", InterviewForm.withRound(d.nextRound()));
        }
        model.addAttribute("maxUploadBytes", storage.getMaxBytes());
        model.addAttribute("maxUploadLabel", storage.getMaxSizeLabel());
        model.addAttribute("allowedTypes", AllowedFileType.DESCRIPTION);
        return "applications/detail";
    }

    // ------------------------------------------------------------------ Edit / delete

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails me, Model model) {
        JobApplication app = service.getForEdit(id, me);
        model.addAttribute("app", app);
        model.addAttribute("form", ApplicationForm.from(app));
        model.addAttribute("editing", true);
        return "applications/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @Valid @ModelAttribute("form") ApplicationForm form, BindingResult br,
                         @AuthenticationPrincipal AppUserDetails me, Model model, RedirectAttributes ra) {
        if (br.hasErrors()) {
            model.addAttribute("app", service.getForEdit(id, me)); // also performs the access check
            model.addAttribute("editing", true);
            return "applications/form";
        }
        service.update(id, form, me);
        ra.addFlashAttribute("success", "Changes saved");
        return "redirect:/applications/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        service.delete(id, me);
        ra.addFlashAttribute("success", "Application and all its documents deleted");
        return "redirect:/applications";
    }

    // ------------------------------------------------------------------ Status

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable UUID id, @Valid @ModelAttribute("statusForm") StatusChangeForm form,
                               BindingResult br, @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        if (!br.hasErrors()) {
            try {
                service.changeStatus(id, form, me);
                ra.addFlashAttribute("success", form.isHistoryOnly() ? "Backfilled a history entry" : "Status updated");
                ra.addFlashAttribute("scrollTo", "status"); // scroll there without a #fragment in the URL
                return "redirect:/applications/" + id;
            } catch (BusinessException e) {
                if (e.getField() != null) {
                    br.rejectValue(e.getField(), "business", e.getMessage());
                } else {
                    br.reject("business", e.getMessage());
                }
            }
        }
        ra.addFlashAttribute("statusForm", form);
        ra.addFlashAttribute(BR + "statusForm", br);
        ra.addFlashAttribute("error", "Status not updated, please check the form");
        ra.addFlashAttribute("scrollTo", "status"); // scroll there without a #fragment in the URL
        return "redirect:/applications/" + id;
    }
}
