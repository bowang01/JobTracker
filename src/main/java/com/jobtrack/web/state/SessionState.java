package com.jobtrack.web.state;

import java.util.function.Supplier;

import jakarta.servlet.http.HttpSession;

/**
 * Keeps list pages' search/filter/sort/page state in the HTTP session instead of the query string,
 * so the address bar only ever shows clean paths such as {@code /applications}.
 * Filter forms POST the new state, it is stored here, and the browser is redirected back to the plain GET page
 * (Post/Redirect/Get), so refreshing or going back never re-submits a form.
 */
public final class SessionState {

    public static final String APPLICATIONS = "jobtrack.state.applications";
    public static final String QUESTIONS = "jobtrack.state.questions";
    public static final String HALL = "jobtrack.state.hall";
    public static final String ADMIN_USERS = "jobtrack.state.adminUsers";
    public static final String ADMIN_QUESTIONS = "jobtrack.state.adminQuestions";

    private SessionState() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(HttpSession session, String key, Supplier<T> defaultValue) {
        Object value = session.getAttribute(key);
        if (value == null) {
            T fresh = defaultValue.get();
            session.setAttribute(key, fresh);
            return fresh;
        }
        return (T) value;
    }

    public static void put(HttpSession session, String key, Object value) {
        session.setAttribute(key, value);
    }

    public static void clear(HttpSession session, String key) {
        session.removeAttribute(key);
    }
}
