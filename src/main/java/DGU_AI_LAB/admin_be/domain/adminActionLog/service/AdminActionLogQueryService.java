package DGU_AI_LAB.admin_be.domain.adminActionLog.service;

import DGU_AI_LAB.admin_be.domain.adminActionLog.*;
import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.*;
import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionLog;
import DGU_AI_LAB.admin_be.domain.adminActionLog.repository.AdminActionLogRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminActionLogQueryService {
    private final EntityManager entityManager;
    private final AdminActionLogRepository repository;
    private final ObjectMapper objectMapper;

    public AdminActionLogPageDTO getLogs(Long adminId, AdminTargetType targetType, String targetId,
            AdminActionType actionType, String requestedRole, Integer httpStatus,
            OffsetDateTime from, OffsetDateTime to, String cursor, int size) {
        if (size < 1 || size > 100 || (from != null && to != null && !from.isBefore(to))
                || (targetId != null && targetId.length() > 255)
                || (requestedRole != null && !Set.of("ADMIN", "USER").contains(requestedRole))
                || (httpStatus != null && (httpStatus < 100 || httpStatus > 599))) throw badRequest();
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(AdminActionLog.class);
        var root = query.from(AdminActionLog.class);
        List<Predicate> predicates = new ArrayList<>();
        if (adminId != null) predicates.add(cb.equal(root.get("adminId"), adminId));
        if (targetType != null) predicates.add(cb.equal(root.get("targetType"), targetType));
        if (targetId != null) predicates.add(cb.equal(root.get("targetId"), targetId));
        if (actionType != null) predicates.add(cb.equal(root.get("actionType"), actionType));
        if (requestedRole != null) predicates.add(cb.equal(root.get("requestedRole"), requestedRole));
        if (httpStatus != null) predicates.add(cb.equal(root.get("httpStatus"), httpStatus.shortValue()));
        if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), utc(from)));
        if (to != null) predicates.add(cb.lessThan(root.get("occurredAt"), utc(to)));
        if (cursor != null) {
            try {
                if (cursor.length() > 256) throw new IllegalArgumentException();
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
                if (parts.length != 2) throw new IllegalArgumentException();
                LocalDateTime time = LocalDateTime.parse(parts[0]);
                long id = Long.parseLong(parts[1]);
                if (id < 1) throw new IllegalArgumentException();
                predicates.add(cb.or(cb.lessThan(root.get("occurredAt"), time),
                        cb.and(cb.equal(root.get("occurredAt"), time), cb.lessThan(root.get("id"), id))));
            } catch (RuntimeException ex) { throw badRequest(); }
        }
        query.where(predicates.toArray(Predicate[]::new)).orderBy(cb.desc(root.get("occurredAt")), cb.desc(root.get("id")));
        List<AdminActionLog> rows = entityManager.createQuery(query).setMaxResults(size + 1).getResultList();
        boolean hasNext = rows.size() > size;
        List<AdminActionLog> page = rows.subList(0, Math.min(size, rows.size()));
        String next = null;
        if (hasNext) {
            AdminActionLog last = page.get(page.size() - 1);
            next = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (last.getOccurredAt() + "|" + last.getId()).getBytes(StandardCharsets.UTF_8));
        }
        return new AdminActionLogPageDTO(page.stream().map(log -> AdminActionLogResponseDTO.from(log, null)).toList(), next, hasNext);
    }

    public AdminActionLogResponseDTO getLog(Long id) {
        AdminActionLog log = repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        try { return AdminActionLogResponseDTO.from(log, objectMapper.readTree(log.getRequestValues())); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Invalid stored audit JSON", ex); }
    }

    private LocalDateTime utc(OffsetDateTime value) { return value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(); }
    private BusinessException badRequest() { return new BusinessException(ErrorCode.BAD_REQUEST); }
}
