package com.jobtrack.service;

import java.util.List;
import java.util.Map;

import com.jobtrack.domain.ApplicationDocument;
import com.jobtrack.domain.DocumentType;
import com.jobtrack.domain.Interview;
import com.jobtrack.domain.JobApplication;
import com.jobtrack.domain.StatusChange;

/** Everything the application detail page needs, loaded in one read-only transaction. */
public record ApplicationDetail(
        JobApplication application,
        List<StatusChange> timeline,
        List<Interview> interviews,
        Map<DocumentType, List<ApplicationDocument>> documentsByType,
        int nextRound) {
}
