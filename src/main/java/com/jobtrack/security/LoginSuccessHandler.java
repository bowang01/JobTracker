package com.jobtrack.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import com.jobtrack.service.UserService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** After sign-in: records the login time and sends admins to the admin overview, users to their dashboard. */
public class LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;

    public LoginSuccessHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof AppUserDetails me) {
            userService.recordLogin(me.getId());
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    /** Chosen per request (the handler is shared, so no mutable target URL). */
    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) {
        if (authentication.getPrincipal() instanceof AppUserDetails me) {
            return homeFor(me);
        }
        return "/dashboard";
    }

    public static String homeFor(AppUserDetails me) {
        return me.isAdmin() ? "/admin" : "/dashboard";
    }
}
