package DGU_AI_LAB.admin_be.domain.adminActionLog;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Repository
public class AdminActionLogQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<AdminActionLog> find(AdminActionLogQuery filter) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<AdminActionLog> query = builder.createQuery(AdminActionLog.class);
        Root<AdminActionLog> row = query.from(AdminActionLog.class);
        Path<LocalDateTime> occurredAt = row.get("occurredAt");
        Path<Long> id = row.get("id");
        List<Predicate> conditions = new ArrayList<>();

        addEquality(builder, row, conditions, "adminId", filter.adminId());
        addEquality(builder, row, conditions, "targetId", filter.targetId());
        addEquality(builder, row, conditions, "requestedRole", filter.requestedRole());
        addEquality(builder, row, conditions, "httpStatus", filter.httpStatus());

        if (filter.targetType() != null) {
            conditions.add(builder.equal(row.get("targetType"), filter.targetType().name()));
        }
        if (filter.actionType() != null) {
            conditions.add(builder.equal(row.get("actionType"), filter.actionType().name()));
        }
        if (filter.from() != null) {
            LocalDateTime from = LocalDateTime.ofInstant(filter.from(), ZoneOffset.UTC);
            conditions.add(builder.greaterThanOrEqualTo(occurredAt, from));
        }
        if (filter.to() != null) {
            LocalDateTime to = LocalDateTime.ofInstant(filter.to(), ZoneOffset.UTC);
            conditions.add(builder.lessThan(occurredAt, to));
        }
        if (filter.before() != null) {
            LocalDateTime before = LocalDateTime.ofInstant(filter.before(), ZoneOffset.UTC);
            conditions.add(builder.or(
                    builder.lessThan(occurredAt, before),
                    builder.and(
                            builder.equal(occurredAt, before),
                            builder.lessThan(id, filter.beforeId()))));
        }

        query.select(row)
                .where(conditions.toArray(Predicate[]::new))
                .orderBy(builder.desc(occurredAt), builder.desc(id));

        return entityManager.createQuery(query)
                .setMaxResults(filter.pageSize() + 1)
                .getResultList();
    }

    private void addEquality(CriteriaBuilder builder, Root<AdminActionLog> row,
                             List<Predicate> conditions, String field, Object value) {
        if (value != null) {
            conditions.add(builder.equal(row.get(field), value));
        }
    }
}