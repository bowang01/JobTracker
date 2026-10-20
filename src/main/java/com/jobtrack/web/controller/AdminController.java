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

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.QuestionCategory;
import com.jobtrack.domain.Visibility;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.service.AdminService;
import com.jobtrack.service.BusinessException;
import com.jobtrack.service.QuestionService;
import com.jobtrack.service.UserService;
import com.jobtrack.web.state.AdminQuestionFilter;
import com.jobtrack.web.state.AdminUserFilter;
import com.jobtrack.web.state.SessionState;

import jakarta.servlet.http.HttpSession;

/**
 * Admin area: monitoring and managing regular users. Admins have no applications of their own;
 * they can open a user's applications read-only through the regular /applications/{id} page.
 * URL-level access to /admin/** is restricted to ROLE_ADMIN in SecurityConfig.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;
    private final QuestionService questionService;

    public AdminController(AdminService adminService, UserService userService, QuestionService questionService) {
        this.adminService = adminService;
        this.userService = userService;
        this.questionService = questionService;
    }

    @GetMapping
    public String overview(Model model) {
        model.addAttribute("o", adminService.overview());
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("recentDays", AdminService.RECENT_DAYS);
        model.addAttribute("activeDays", AdminService.ACTIVE_DAYS);
        model.addAttribute("upcomingDays", AdminService.UPCOMING_DAYS);
        return "admin/overview";
    }

    /** User list. Search state lives in the session (no query string). */
    @GetMapping("/users")
    public String users(HttpSession session, Model model) {
        AdminUserFilter f = userFilter(session);
        model.addAttribute("rows", adminService.searchUsers(f.getQ(), f.enabledFilter()));
        model.addAttribute("q", f.getQ());
        model.addAttribute("state", f.getState());
        return "admin/users";
    }

    @PostMapping("/users/filter")
    public String filterUsers(@RequestParam(required = false) String q,
                              @RequestParam(required = false) String state, HttpSession session) {
        AdminUserFilter f = userFilter(session);
        f.setQ(q);
        f.setState(state);
        return "redirect:/admin/users";
    }

    @PostMapping("/users/reset")
    public String resetUsers(HttpSession session) {
        SessionState.clear(session, SessionState.ADMIN_USERS);
        return "redirect:/admin/users";
    }

    @GetMapping("/users/{id}")
    public String userDetail(@PathVariable UUID id, Model model) {
        model.addAttribute("u", adminService.userDetail(id));
        model.addAttribute("statuses", ApplicationStatus.values());
        return "admin/user-detail";
    }

    /** Every user's interview questions, public and private (never their answers). Filters live in the session. */
    @GetMapping("/questions")
    public String questions(HttpSession session, @AuthenticationPrincipal AppUserDetails me, Model model) {
        AdminQuestionFilter f = questionFilter(session);
        model.addAttribute("page", questionService.allForAdmin(me, f.getCategory(), f.getVisibility(),
                f.getOwnerId(), f.getQ(), f.getPage()));
        model.addAttribute("categories", QuestionCategory.values());
        model.addAttribute("visibilities", Visibility.values());
        model.addAttribute("category", f.getCategory());
        model.addAttribute("visibility", f.getVisibility());
        model.addAttribute("ownerId", f.getOwnerId());
        model.addAttribute("owner", f.getOwnerId() == null ? null : userService.get(f.getOwnerId()));
        model.addAttribute("q", f.getQ());
        return "admin/questions";
    }

    /**
     * Applies the filter form. Also used by links elsewhere ("this user's questions", "public questions"),
     * which post only the fields they care about; everything not posted is cleared.
     */
    @PostMapping("/questions/filter")
    public String filterQuestions(@RequestParam(required = false) String category,
                                  @RequestParam(required = false) String visibility,
                                  @RequestParam(required = false) UUID ownerId,
                                  @RequestParam(required = false) String q, HttpSession session) {
        AdminQuestionFilter f = questionFilter(session);
        f.setCategory(parse(QuestionCategory.class, category));
        f.setVisibility(parse(Visibility.class, visibility));
        f.setOwnerId(ownerId);
        f.setQ(q);
        f.setPage(0);
        return "redirect:/admin/questions";
    }

    @PostMapping("/questions/page")
    public String questionsPage(@RequestParam(defaultValue = "0") int page, HttpSession session) {
        questionFilter(session).setPage(page);
        return "redirect:/admin/questions";
    }

    @PostMapping("/questions/reset")
    public String resetQuestions(HttpSession session) {
        SessionState.clear(session, SessionState.ADMIN_QUESTIONS);
        return "redirect:/admin/questions";
    }

    private static AdminUserFilter userFilter(HttpSession session) {
        return SessionState.get(session, SessionState.ADMIN_USERS, AdminUserFilter::new);
    }

    private static AdminQuestionFilter questionFilter(HttpSession session) {
        return SessionState.get(session, SessionState.ADMIN_QUESTIONS, AdminQuestionFilter::new);
    }

    @PostMapping("/users/{id}/enabled")
    public String setEnabled(@PathVariable UUID id, @RequestParam boolean enabled,
                             @RequestParam(required = false) String back,
                             @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        run(ra, () -> userService.setEnabled(id, enabled, me), enabled ? "Account enabled" : "Account disabled");
        return "detail".equals(back) ? "redirect:/admin/users/" + id : "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/delete")
    public String delete(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        run(ra, () -> userService.deleteUser(id, me), "User and all their data deleted");
        return "redirect:/admin/users";
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

    private static void run(RedirectAttributes ra, Runnable action, String successMessage) {
        try {
            action.run();
            ra.addFlashAttribute("success", successMessage);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
    }
}
