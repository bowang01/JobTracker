package com.jobtrack.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.jobtrack.domain.JobApplication;

/** Hand-written queries for job applications; implemented in {@code impl.JobApplicationRepositoryCustomImpl}. */
public interface JobApplicationRepositoryCustom {

    /** Dynamic search: only the conditions that are set in the filter are applied. */
    Page<JobApplication> search(ApplicationFilter filter, Pageable pageable);
}
