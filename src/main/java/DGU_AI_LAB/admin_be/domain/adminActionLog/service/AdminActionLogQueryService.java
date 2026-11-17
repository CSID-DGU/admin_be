package DGU_AI_LAB.admin_be.domain.adminActionLog.service;

import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminActionType;
import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminTargetType;
import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.response.AdminActionLogPageResponseDTO;
import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.response.AdminActionLogResponseDTO;
import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionLog;
import DGU_AI_LAB.admin_be.domain.adminActionLog.repository.AdminActionLogRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminActionLogQueryService {
    private final EntityManager entityManager;
    private final AdminActionLogRepository repository;
    private final ObjectMapper objectMapper;

    public AdminActionLogPageResponseDTO getLogs(
            Long adminId,
            AdminTargetType targetType,
            String targetId,
            AdminActionType actionType,
            String requestedRole,
            Integer httpStatus,
            OffsetDateTime from,
            OffsetDateTime to,
            String cursor,
            int size) {
        if (size < 1
                || size > 100
                || (from != null && to != null && !from.isBefore(to))
                || (targetId != null && targetId.length() > 255)
                || (requestedRole != null && !Set.of("ADMIN", "USER").contains(requestedRole))
                || (httpStatus != null && (httpStatus < 100 || httpStatus > 599)))
            throw badRequest();
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(AdminActionLog.class);
        var root = query.from(AdminActionLog.class);
        List<Predicate> predicates = new ArrayList<>();
        if (adminId != null) predicates.add(cb.equal(root.get("adminId"), adminId));
        if (targetType != null) predicates.add(cb.equal(root.get("targetType"), targetType));
        if (targetId != null) predicates.add(cb.equal(root.get("targetId"), targetId));
        if (actionType != null) predicates.add(cb.equal(root.get("actionType"), actionType));
        if (requestedRole != null)
            predicates.add(cb.equal(root.get("requestedRole"), requestedRole));
        if (httpStatus != null)
            predicates.add(cb.equal(root.get("httpStatus"), httpStatus.shortValue()));
        if (from != null)
            predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), utc(from)));
        if (to != null) predicates.add(cb.lessThan(root.get("occurredAt"), utc(to)));
        if (cursor != null) {
            try {
                if (cursor.length() > 256) throw new IllegalArgumentException();
                String[] parts =
                        new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
                                .split("\\|", -1);
                if (parts.length != 2) throw new IllegalArgumentException();
                LocalDateTime time = LocalDateTime.parse(parts[0]);
                long id = Long.parseLong(parts[1]);
                if (id < 1) throw new IllegalArgumentException();
                predicates.add(
                        cb.or(
                                cb.lessThan(root.get("occurredAt"), time),
                                cb.and(
                                        cb.equal(root.get("occurredAt"), time),
                                        cb.lessThan(root.get("id"), id))));
            } catch (RuntimeException ex) {
                throw badRequest();
            }
        }
        query.where(predicates.toArray(Predicate[]::new))
                .orderBy(cb.desc(root.get("occurredAt")), cb.desc(root.get("id")));
        List<AdminActionLog> rows =
                entityManager.createQuery(query).setMaxResults(size + 1).getResultList();
        boolean hasNext = rows.size() > size;
        List<AdminActionLog> page = rows.subList(0, Math.min(size, rows.size()));
        String next = null;
        if (hasNext) {
            AdminActionLog last = page.get(page.size() - 1);
            next =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(
                                    (last.getOccurredAt() + "|" + last.getId())
                                            .getBytes(StandardCharsets.UTF_8));
        }
        return new AdminActionLogPageResponseDTO(
                page.stream().map(log -> AdminActionLogResponseDTO.fromEntity(log, null)).toList(),
                next,
                hasNext);
    }

    public AdminActionLogResponseDTO getLog(Long id) {
        AdminActionLog log =
                repository
                        .findById(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        try {
            return AdminActionLogResponseDTO.fromEntity(
                    log, objectMapper.readTree(log.getRequestValues()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid stored audit JSON", ex);
        }
    }

    private LocalDateTime utc(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private BusinessException badRequest() {
        return new BusinessException(ErrorCode.BAD_REQUEST);
    }
}
