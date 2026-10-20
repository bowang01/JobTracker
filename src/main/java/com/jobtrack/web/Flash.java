package com.jobtrack.web;

import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Flash messages from outside Spring MVC controllers (security handlers and filters), so that messages such as
 * "signed out" travel in the session instead of the query string (no more /login?logout in the address bar).
 */
public final class Flash {

    private static final SessionFlashMapManager MANAGER = new SessionFlashMapManager();

    private Flash() {
    }

    /** Stores a one-time model attribute for the next request to {@code targetPath}. */
    public static void put(HttpServletRequest request, HttpServletResponse response, String targetPath,
                           String key, Object value) {
        FlashMap flash = new FlashMap();
        flash.put(key, value);
        flash.setTargetRequestPath(request.getContextPath() + targetPath);
        MANAGER.saveOutputFlashMap(flash, request, response);
    }
}
