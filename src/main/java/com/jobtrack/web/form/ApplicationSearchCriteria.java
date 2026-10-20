package com.jobtrack.web.form;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import com.jobtrack.domain.ApplicationStatus;

/**
 * Search, filter, sort and page state of the application list. Kept in the session (see SessionState),
 * never in the query string. Sort fields are whitelisted.
 */
public class ApplicationSearchCriteria implements Serializable {

    public static final Map<String, String> SORT_FIELDS = Map.of(
            "updated", "updatedAt",
            "created", "createdAt",
            "applied", "appliedDate",
            "company", "company",
            "position", "positionTitle",
            "status", "status");

    private String company;
    private String position;
    private ApplicationStatus status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate appliedFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate appliedTo;

    /** Admins only: filter by owner. Ignored for regular users. */
    private UUID ownerId;

    private String sort = "updated";
    private String dir = "desc";
    private int page;

    /** Default first-click direction for a column: newest first for dates, A-Z for text. */
    public static String defaultDir(String sortKey) {
        return "company".equals(sortKey) || "position".equals(sortKey) || "status".equals(sortKey) ? "asc" : "desc";
    }

    /** Clicking a column header: flip the direction of the active column, or switch to the new column. */
    public void toggleSort(String sortKey) {
        if (!SORT_FIELDS.containsKey(sortKey)) {
            return;
        }
        if (sortKey.equals(sort)) {
            dir = "asc".equals(dir) ? "desc" : "asc";
        } else {
            sort = sortKey;
            dir = defaultDir(sortKey);
        }
        page = 0;
    }

    /** Takes the filter fields from a submitted filter form; keeps the current sort; goes back to page 1. */
    public void applyFilters(ApplicationSearchCriteria submitted) {
        company = submitted.company;
        position = submitted.position;
        status = submitted.status;
        appliedFrom = submitted.appliedFrom;
        appliedTo = submitted.appliedTo;
        ownerId = submitted.ownerId;
        page = 0;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(page, 0);
    }

    public Sort toSort() {
        String field = SORT_FIELDS.getOrDefault(sort, "updatedAt");
        Sort.Direction direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field).and(Sort.by(Sort.Direction.DESC, "id"));
    }

    public boolean isFiltered() {
        return hasText(company) || hasText(position) || status != null
                || appliedFrom != null || appliedTo != null || ownerId != null;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public LocalDate getAppliedFrom() {
        return appliedFrom;
    }

    public void setAppliedFrom(LocalDate appliedFrom) {
        this.appliedFrom = appliedFrom;
    }

    public LocalDate getAppliedTo() {
        return appliedTo;
    }

    public void setAppliedTo(LocalDate appliedTo) {
        this.appliedTo = appliedTo;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = SORT_FIELDS.containsKey(sort) ? sort : "updated";
    }

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = "asc".equalsIgnoreCase(dir) ? "asc" : "desc";
    }
}
