package DGU_AI_LAB.admin_be.domain.adminActionLog.service;

import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.AdminActionLogPageResponseDTO;
import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.AdminActionLogResponseDTO;
import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.AdminActionLogSearchRequestDTO;
import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionLog;
import DGU_AI_LAB.admin_be.domain.adminActionLog.repository.AdminActionLogRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminActionLogQueryService {

    /** 실패의 기준. 4xx(REJECTED)와 5xx(ERROR)가 실패다 — {@code AdminActionResult}와 같은 경계. */
    private static final int FAILURE_MIN_STATUS = 400;

    private final AdminActionLogRepository repository;
    private final ObjectMapper objectMapper;

    public AdminActionLogPageResponseDTO search(AdminActionLogSearchRequestDTO condition) {
        if (condition.targetId() != null && condition.targetType() == null) {
            throw new BusinessException("targetId 는 targetType 과 함께 줘야 합니다.", ErrorCode.BAD_REQUEST);
        }
        int size = condition.sizeOrDefault();
        // 한 건 더 읽어 다음 페이지가 있는지 본다.
        List<AdminActionLog> rows = repository.search(
                condition.targetType() == null ? null : condition.targetType().name(),
                condition.targetId(),
                condition.adminId(),
                condition.action() == null ? null : condition.action().name(),
                condition.from(),
                condition.to(),
                condition.isFailedOnly() ? FAILURE_MIN_STATUS : null,
                condition.cursor(),
                PageRequest.ofSize(size + 1));

        boolean hasNext = rows.size() > size;
        List<AdminActionLog> page = hasNext ? rows.subList(0, size) : rows;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getAdminActionLogId() : null;

        return new AdminActionLogPageResponseDTO(page.stream().map(this::toResponse).toList(), nextCursor);
    }

    private AdminActionLogResponseDTO toResponse(AdminActionLog log) {
        return AdminActionLogResponseDTO.of(log, parse(log.getPathVariables()), parse(log.getQueryParams()),
                parse(log.getRequestBody()));
    }

    private JsonNode parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.getNodeFactory().textNode(json);
        }
    }
}
