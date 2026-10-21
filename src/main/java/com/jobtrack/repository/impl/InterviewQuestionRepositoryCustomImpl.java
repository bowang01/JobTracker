package com.jobtrack.repository.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.jobtrack.domain.AnswerRating;
import com.jobtrack.domain.InterviewQuestion;
import com.jobtrack.domain.ProgrammingLanguage;
import com.jobtrack.domain.QuestionCategory;
import com.jobtrack.domain.User;
import com.jobtrack.domain.Visibility;
import com.jobtrack.repository.InterviewQuestionRepositoryCustom;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Criteria API implementation of {@link InterviewQuestionRepositoryCustom}.
 * Note: public and admin searches never look at {@code answerText}, so searching cannot reveal private answers.
 */
@Repository
public class InterviewQuestionRepositoryCustomImpl implements InterviewQuestionRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<InterviewQuestion> searchOwn(UUID ownerId, QuestionCategory category, ProgrammingLanguage language,
                                             AnswerRating rating, String keyword) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<InterviewQuestion> query = cb.createQuery(InterviewQuestion.class);
        Root<InterviewQuestion> q = query.from(InterviewQuestion.class);

        List<Predicate> ps = new ArrayList<>();
        ps.add(cb.equal(q.get("owner").get("id"), ownerId));
        if (category != null) {
            ps.add(cb.equal(q.get("category"), category));
        }
        if (language != null) {
            ps.add(cb.equal(q.get("language"), language));
        }
        if (rating != null) {
            ps.add(cb.equal(q.get("selfRating"), rating));
        }
        String like = CriteriaSupport.containsPattern(keyword);
        if (like != null) {
            // The owner may search their own answers
            ps.add(cb.or(CriteriaSupport.contains(cb, q.get("questionText"), like),
                    CriteriaSupport.contains(cb, q.get("answerText"), like)));
        }
        query.select(q).where(ps.toArray(new Predicate[0]))
                .orderBy(cb.desc(q.get("updatedAt")), cb.desc(q.get("id")));
        return em.createQuery(query).getResultList();
    }

    @Override
    public Page<InterviewQuestion> searchPublic(QuestionCategory category, ProgrammingLanguage language, String keyword,
                                                Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        String like = CriteriaSupport.containsPattern(keyword);

        CriteriaQuery<InterviewQuestion> query = cb.createQuery(InterviewQuestion.class);
        Root<InterviewQuestion> q = query.from(InterviewQuestion.class);
        query.select(q).where(publicConditions(cb, q, category, language, like));

        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<InterviewQuestion> cq = count.from(InterviewQuestion.class);
        count.select(cb.count(cq)).where(publicConditions(cb, cq, category, language, like));

        return CriteriaSupport.page(em, query, q, count, pageable);
    }

    private static Predicate[] publicConditions(CriteriaBuilder cb, Root<InterviewQuestion> q,
                                                QuestionCategory category, ProgrammingLanguage language, String like) {
        List<Predicate> ps = new ArrayList<>();
        ps.add(cb.equal(q.get("visibility"), Visibility.PUBLIC));
        if (category != null) {
            ps.add(cb.equal(q.get("category"), category));
        }
        if (language != null) {
            ps.add(cb.equal(q.get("language"), language));
        }
        if (like != null) {
            ps.add(CriteriaSupport.contains(cb, q.get("questionText"), like));
        }
        return ps.toArray(new Predicate[0]);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Page<InterviewQuestion> searchAll(QuestionCategory category, ProgrammingLanguage language, Visibility visibility, UUID ownerId,
                                             String keyword, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        String like = CriteriaSupport.containsPattern(keyword);

        CriteriaQuery<InterviewQuestion> query = cb.createQuery(InterviewQuestion.class);
        Root<InterviewQuestion> q = query.from(InterviewQuestion.class);
        // Fetch the owner in the same query (the admin view shows the owner's email)
        Join<InterviewQuestion, User> owner = (Join<InterviewQuestion, User>) q.<InterviewQuestion, User>fetch("owner", JoinType.INNER);
        query.select(q).where(allConditions(cb, q, owner, category, language, visibility, ownerId, like));

        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<InterviewQuestion> cq = count.from(InterviewQuestion.class);
        Join<InterviewQuestion, User> countOwner = cq.join("owner", JoinType.INNER);
        count.select(cb.count(cq)).where(allConditions(cb, cq, countOwner, category, language, visibility, ownerId, like));

        return CriteriaSupport.page(em, query, q, count, pageable);
    }

    private static Predicate[] allConditions(CriteriaBuilder cb, Root<InterviewQuestion> q, Join<InterviewQuestion, User> owner,
                                             QuestionCategory category, ProgrammingLanguage language, Visibility visibility,
                                             UUID ownerId, String like) {
        List<Predicate> ps = new ArrayList<>();
        if (category != null) {
            ps.add(cb.equal(q.get("category"), category));
        }
        if (language != null) {
            ps.add(cb.equal(q.get("language"), language));
        }
        if (visibility != null) {
            ps.add(cb.equal(q.get("visibility"), visibility));
        }
        if (ownerId != null) {
            ps.add(cb.equal(owner.get("id"), ownerId));
        }
        if (like != null) {
            ps.add(cb.or(CriteriaSupport.contains(cb, q.get("questionText"), like),
                    CriteriaSupport.contains(cb, owner.get("email"), like)));
        }
        return ps.toArray(new Predicate[0]);
    }
}
