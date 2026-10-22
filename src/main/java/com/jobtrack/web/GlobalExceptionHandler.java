package com.jobtrack.web;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.support.RequestContextUtils;

import com.jobtrack.service.NotFoundException;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final String APPLICATION_PAGE = "^/applications/" + UUID_PATTERN + "$";

    @ExceptionHandler({NotFoundException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound() {
        return "error/404";
    }

    /** Uploads above the servlet limit (20MB): redirect back with a message. */
    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public String uploadFailed(Exception ex, HttpServletRequest request) {
        log.info("Upload rejected: {}", ex.getMessage());
        String msg = ex instanceof MaxUploadSizeExceededException
                ? "File too large; upload failed"
                : "Upload failed, please try again";
        RequestContextUtils.getOutputFlashMap(request).put("error", msg);
        RequestContextUtils.getOutputFlashMap(request).put("scrollTo", "documents");
        return "redirect:" + safeBackPath(request);
    }

    /** Only redirect back to pages under /applications on this site (no open redirect). */
    private static String safeBackPath(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                String path = URI.create(referer).getPath();
                if (path != null && (path.matches(APPLICATION_PAGE) || path.equals("/applications/new"))) {
                    return path;
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid Referer, use the default path
            }
        }
        String uri = request.getRequestURI();
        if (uri != null && uri.matches("^/applications/" + UUID_PATTERN + "/documents$")) {
            return uri.substring(0, uri.length() - "/documents".length());
        }
        return "/applications";
    }
}
