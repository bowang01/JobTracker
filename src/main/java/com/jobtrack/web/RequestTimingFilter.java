package com.jobtrack.web;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jobtrack.config.SqlStatementCounter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Logs how long each request took and how many SQL statements it ran, e.g.
 * {@code GET /applications -> 200 in 42 ms, 3 SQL}.
 * <ul>
 *   <li>Requests slower than {@code jobtrack.slow-request-ms} are logged at WARN;</li>
 *   <li>all other requests at DEBUG (turn on with {@code logging.level.com.jobtrack.web.RequestTimingFilter=DEBUG}).</li>
 * </ul>
 * Runs first, so the time includes Spring Security, the controller and template rendering.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTimingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestTimingFilter.class);

    private final long slowMs;

    public RequestTimingFilter(@Value("${jobtrack.slow-request-ms:500}") long slowMs) {
        this.slowMs = slowMs;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        SqlStatementCounter.reset();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            int sql = SqlStatementCounter.count();
            SqlStatementCounter.clear();
            if (ms >= slowMs) {
                log.warn("SLOW {} {} -> {} in {} ms, {} SQL", request.getMethod(), request.getRequestURI(),
                        response.getStatus(), ms, sql);
            } else if (log.isDebugEnabled()) {
                log.debug("{} {} -> {} in {} ms, {} SQL", request.getMethod(), request.getRequestURI(),
                        response.getStatus(), ms, sql);
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/icons/")
                || path.equals("/manifest.json") || path.equals("/favicon.ico");
    }
}
