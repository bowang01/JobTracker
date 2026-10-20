package com.jobtrack.web.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.service.DashboardService;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserDetails me, Model model) {
        if (me.isAdmin()) {
            return "redirect:/admin"; // admins have no personal dashboard
        }
        model.addAttribute("view", dashboardService.build(me.getId()));
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("upcomingDays", DashboardService.UPCOMING_DAYS);
        model.addAttribute("recentDays", DashboardService.RECENT_DAYS);
        return "dashboard";
    }
}
