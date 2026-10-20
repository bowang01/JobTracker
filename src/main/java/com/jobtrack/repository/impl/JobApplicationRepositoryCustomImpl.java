package com.jobtrack.repository.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.jobtrack.domain.JobApplication;
import com.jobtrack.repository.ApplicationFilter;
import com.jobtrack.repository.JobApplicationRepositoryCustom;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Criteria API implementation of {@link JobApplicationRepositoryCustom}. Spring Data finds it by its name
 * (fragment interface name + "Impl") and merges it into {@code JobApplicationRepository}.
 */
@Repository
public class JobApplicationRepositoryCustomImpl implements JobApplicationRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<JobApplication> search(ApplicationFilter filter, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<JobApplication> query = cb.createQuery(JobApplication.class);
        Root<JobApplication> root = query.from(JobApplication.class);
        // Load the owner in the same query; otherwise the EAGER owner costs one extra SELECT per distinct owner
        root.fetch("owner", JoinType.INNER);
        query.select(root).where(conditions(cb, root, filter));

        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<JobApplication> countRoot = count.from(JobApplication.class);
        count.select(cb.count(countRoot)).where(conditions(cb, countRoot, filter));

        return CriteriaSupport.page(em, query, root, count, pageable);
    }

    private static Predicate[] conditions(CriteriaBuilder cb, Root<JobApplication> root, ApplicationFilter f) {
        List<Predicate> ps = new ArrayList<>();
        if (f.ownerId() != null) {
            ps.add(cb.equal(root.get("owner").get("id"), f.ownerId()));
        }
        String company = CriteriaSupport.containsPattern(f.company());
        if (company != null) {
            ps.add(CriteriaSupport.contains(cb, root.get("company"), company));
        }
        String position = CriteriaSupport.containsPattern(f.position());
        if (position != null) {
            ps.add(CriteriaSupport.contains(cb, root.get("positionTitle"), position));
        }
        if (f.status() != null) {
            ps.add(cb.equal(root.get("status"), f.status()));
        }
        if (f.appliedFrom() != null) {
            ps.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("appliedDate"), f.appliedFrom()));
        }
        if (f.appliedTo() != null) {
            ps.add(cb.lessThanOrEqualTo(root.<LocalDate>get("appliedDate"), f.appliedTo()));
        }
        return ps.toArray(new Predicate[0]);
    }
}
