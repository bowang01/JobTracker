package com.jobtrack.repository.impl;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.data.support.PageableExecutionUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/** Small helpers shared by the hand-written Criteria API repository implementations. */
final class CriteriaSupport {

    /** LIKE escape character. Not a backslash: that is itself an escape in MySQL string literals. */
    static final char ESCAPE = '!';

    private CriteriaSupport() {
    }

    /** "%keyword%" in lower case with LIKE wildcards escaped, or null when the keyword is blank. */
    static String containsPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String k = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + k + "%";
    }

    /** Case-insensitive "contains" on a string attribute. */
    static Predicate contains(CriteriaBuilder cb, Expression<String> attribute, String pattern) {
        return cb.like(cb.lower(attribute), pattern, ESCAPE);
    }

    /** Runs a paged query: applies the pageable's sort to the data query and runs a separate count query. */
    static <T> Page<T> page(EntityManager em, CriteriaQuery<T> query, Root<T> root,
                            CriteriaQuery<Long> countQuery, Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            query.orderBy(QueryUtils.toOrders(pageable.getSort(), root, em.getCriteriaBuilder()));
        }
        TypedQuery<T> typed = em.createQuery(query);
        if (pageable.isPaged()) {
            typed.setFirstResult((int) pageable.getOffset());
            typed.setMaxResults(pageable.getPageSize());
        }
        List<T> content = typed.getResultList();
        return PageableExecutionUtils.getPage(content, pageable, () -> em.createQuery(countQuery).getSingleResult());
    }
}
