package com.jobtrack.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jobtrack.repository.UserRepository;
import com.jobtrack.web.Flash;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * After an admin disables or deletes an account or changes its role, that user's existing session is ended
 * on their next request. Costs one primary-key lookup per authenticated request, which is fine for an MVP.
 */
public class AccountStatusFilter extends OncePerRequestFilter {

    private final UserRepository users;

    public AccountStatusFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserDetails me) {
            boolean stillValid = users.findById(me.getId())
                    .map(u -> u.isEnabled() && u.getRole() == me.getRole())
                    .orElse(false);
            if (!stillValid) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                Flash.put(request, response, "/login", "info", "Your account has changed. Please sign in again.");
                response.sendRedirect(request.getContextPath() + "/login");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/css/") || path.startsWith("/js/") || path.equals("/login") || path.startsWith("/error");
    }
}
