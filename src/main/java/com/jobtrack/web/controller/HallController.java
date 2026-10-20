package com.jobtrack.web.controller;

import java.util.Locale;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.jobtrack.domain.InterviewQuestion;
import com.jobtrack.domain.QuestionCategory;
import com.jobtrack.domain.Visibility;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.service.BusinessException;
import com.jobtrack.service.QuestionService;
import com.jobtrack.web.state.HallFilter;
import com.jobtrack.web.state.SessionState;

import jakarta.servlet.http.HttpSession;

/**
 * Interview hall: public questions shared by all users (question only; answers stay private).
 * Viewing: any signed-in user; admins see every question, including private ones, with the owner. Copy to my bank: regular users. Take out of the hall: admins (SecurityConfig).
 */
@Controller
@RequestMapping("/hall")
public class HallController {

    private final QuestionService service;

    public HallController(QuestionService service) {
        this.service = service;
    }

    /** The hall page. Tab, search and page live in the session (no query string); see the POST handlers below. */
    @GetMapping
    public String hall(HttpSession session, @AuthenticationPrincipal AppUserDetails me, Model model) {
        HallFilter f = filter(session);
        // Only admins can filter by visibility (users only ever see public questions)
        Visibility vis = me.isAdmin() ? f.getVisibility() : null;
        model.addAttribute("page", service.hall(me, f.getCategory(), vis, f.getQ(), f.getPage()));
        model.addAttribute("counts", service.hallCounts(me));
        model.addAttribute("category", f.getCategory());
        model.addAttribute("visibility", vis);
        model.addAttribute("visibilities", Visibility.values());
        model.addAttribute("q", f.getQ());
        return "questions/hall";
    }

    @PostMapping("/tab")
    public String tab(@RequestParam(required = false) String category, HttpSession session) {
        HallFilter f = filter(session);
        f.setCategory(parseCategory(category));
        f.setPage(0);
        return "redirect:/hall";
    }

    @PostMapping("/filter")
    public String applyFilter(@RequestParam(required = false) String q,
                              @RequestParam(required = false) String visibility, HttpSession session) {
        HallFilter f = filter(session);
        f.setQ(q);
        f.setVisibility(parseVisibility(visibility));
        f.setPage(0);
        return "redirect:/hall";
    }

    @PostMapping("/page")
    public String page(@RequestParam(defaultValue = "0") int page, HttpSession session) {
        filter(session).setPage(page);
        return "redirect:/hall";
    }

    @PostMapping("/reset")
    public String reset(HttpSession session) {
        HallFilter f = filter(session);
        f.setQ(null);
        f.setVisibility(null);
        f.setPage(0);
        return "redirect:/hall";
    }

    private static HallFilter filter(HttpSession session) {
        return SessionState.get(session, SessionState.HALL, HallFilter::new);
    }

    @PostMapping("/{id}/copy")
    public String copy(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        try {
            InterviewQuestion copy = service.copyToMine(id, me);
            ra.addFlashAttribute("success", "Added to your questions as a private question. You can write your own answer now.");
            return "redirect:/questions/" + copy.getId() + "/edit";
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/hall";
        }
    }

    @PostMapping("/{id}/unpublish")
    public String unpublish(@PathVariable UUID id, @RequestParam(required = false) String back,
                            @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        service.unpublish(id, me);
        ra.addFlashAttribute("success", "Question removed from the interview hall (it is now private to its owner)");
        return "admin".equals(back) ? "redirect:/admin/questions" : "redirect:/hall";
    }

    private static Visibility parseVisibility(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Visibility.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static QuestionCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return QuestionCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
