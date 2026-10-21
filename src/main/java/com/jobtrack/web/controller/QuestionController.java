package com.jobtrack.web.controller;

import java.util.Locale;
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

import com.jobtrack.config.DemoAccount;
import com.jobtrack.domain.AnswerRating;
import com.jobtrack.domain.InterviewQuestion;
import com.jobtrack.domain.ProgrammingLanguage;
import com.jobtrack.domain.QuestionCategory;
import com.jobtrack.domain.Visibility;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.service.QuestionService;
import com.jobtrack.web.form.QuestionForm;

import com.jobtrack.web.state.QuestionFilter;
import com.jobtrack.web.state.SessionState;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/** Interview question bank (regular users only; see SecurityConfig). */
@Controller
@RequestMapping("/questions")
public class QuestionController {

    /** The interview hall is the visitors' landing page, so the shared demo account cannot publish to it. */
    static final String DEMO_NO_SHARING = "The demo account cannot share questions to the interview hall. Keep it private, or create your own account to share.";

    private final QuestionService service;
    private final DemoAccount demo;

    public QuestionController(QuestionService service, DemoAccount demo) {
        this.service = service;
        this.demo = demo;
    }

    private void rejectDemoSharing(QuestionForm form, BindingResult br, AppUserDetails me) {
        if (demo.isDemo(me) && form.getVisibility() == Visibility.PUBLIC) {
            br.rejectValue("visibility", "demo", DEMO_NO_SHARING);
        }
    }

    @ModelAttribute
    void referenceData(Model model) {
        model.addAttribute("categories", QuestionCategory.values());
        model.addAttribute("languages", ProgrammingLanguage.values());
        model.addAttribute("ratings", AnswerRating.values());
        model.addAttribute("visibilities", Visibility.values());
    }

    /** Lists questions. The tab/filter state lives in the session (no query string); see the POST handlers below. */
    @GetMapping
    public String list(HttpSession session, @AuthenticationPrincipal AppUserDetails me, Model model) {
        QuestionFilter f = filter(session);
        model.addAttribute("questions", service.search(me, f.getCategory(), f.getLanguage(), f.getRating(), f.getQ()));
        model.addAttribute("counts", service.counts(me));
        model.addAttribute("filter", f);
        model.addAttribute("category", f.getCategory());
        model.addAttribute("rating", f.getRating());
        model.addAttribute("language", f.getLanguage());
        model.addAttribute("q", f.getQ());
        return "questions/list";
    }

    /** Switches the Technical / Behavioral / All tab and keeps the other filters. */
    @PostMapping("/tab")
    public String tab(@RequestParam(required = false) String category, HttpSession session) {
        filter(session).setCategory(parse(QuestionCategory.class, category));
        return "redirect:/questions";
    }

    /** Applies the search form (keyword, language and rating); the current tab is kept. */
    @PostMapping("/filter")
    public String applyFilter(@RequestParam(required = false) String rating,
                              @RequestParam(required = false) String language,
                              @RequestParam(required = false) String q, HttpSession session) {
        QuestionFilter f = filter(session);
        f.setRating(parse(AnswerRating.class, rating));
        f.setLanguage(parse(ProgrammingLanguage.class, language));
        f.setQ(q);
        return "redirect:/questions";
    }

    @PostMapping("/reset")
    public String reset(HttpSession session) {
        QuestionFilter f = filter(session);
        f.setRating(null);
        f.setLanguage(null);
        f.setQ(null);
        return "redirect:/questions";
    }

    /** "Share a question" in the interview hall: opens the form with Public preselected (no ?visibility= in the URL). */
    @PostMapping("/share")
    public String share(@AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        if (demo.isDemo(me)) {
            ra.addFlashAttribute("error", DEMO_NO_SHARING);
            return "redirect:/hall";
        }
        ra.addFlashAttribute("presetVisibility", Visibility.PUBLIC);
        return "redirect:/questions/new";
    }

    /** New question form; the type defaults to the current tab, or to the type of the question just saved. */
    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        QuestionCategory category = model.containsAttribute("presetCategory")
                ? (QuestionCategory) model.getAttribute("presetCategory")
                : filter(session).getCategory();
        QuestionForm form = QuestionForm.withCategory(category);
        if (model.getAttribute("presetVisibility") == Visibility.PUBLIC) {
            form.setVisibility(Visibility.PUBLIC);
        }
        model.addAttribute("form", form);
        model.addAttribute("editing", false);
        return "questions/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") QuestionForm form, BindingResult br,
                         @RequestParam(required = false) String another, HttpSession session,
                         @AuthenticationPrincipal AppUserDetails me, Model model, RedirectAttributes ra) {
        rejectDemoSharing(form, br, me);
        if (br.hasErrors()) {
            model.addAttribute("editing", false);
            return "questions/form";
        }
        InterviewQuestion saved = service.create(form, me);
        ra.addFlashAttribute("success", saved.getCategory().getLabel() + " question saved");
        if (another != null) {
            // "Save and add another": stay on the form with the same category to enter several questions quickly
            ra.addFlashAttribute("presetCategory", saved.getCategory());
            return "redirect:/questions/new";
        }
        filter(session).setCategory(saved.getCategory()); // show the tab the question was saved to
        return "redirect:/questions";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails me, Model model) {
        InterviewQuestion question = service.get(id, me);
        model.addAttribute("question", question);
        model.addAttribute("form", QuestionForm.from(question));
        model.addAttribute("editing", true);
        return "questions/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @Valid @ModelAttribute("form") QuestionForm form, BindingResult br,
                         HttpSession session,
                         @AuthenticationPrincipal AppUserDetails me, Model model, RedirectAttributes ra) {
        rejectDemoSharing(form, br, me);
        if (br.hasErrors()) {
            model.addAttribute("question", service.get(id, me)); // also performs the access check
            model.addAttribute("editing", true);
            return "questions/form";
        }
        service.update(id, form, me);
        ra.addFlashAttribute("success", "Question updated");
        filter(session).setCategory(form.getCategory());
        return "redirect:/questions";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        service.delete(id, me);
        ra.addFlashAttribute("success", "Question deleted");
        return "redirect:/questions";
    }

    private static QuestionFilter filter(HttpSession session) {
        return SessionState.get(session, SessionState.QUESTIONS, QuestionFilter::new);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
