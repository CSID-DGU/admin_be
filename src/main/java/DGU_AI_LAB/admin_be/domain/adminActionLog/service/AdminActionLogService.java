package DGU_AI_LAB.admin_be.domain.adminActionLog.service;

import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminActionType;
import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.request.AdminActionLogRequestDTO;
import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.response.AdminActionLogResponseDTO;
import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionLog;
import DGU_AI_LAB.admin_be.domain.adminActionLog.repository.AdminActionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminActionLogService {

    private final AdminActionLogRepository adminActionLogRepository;

    @Transactional(timeout = 2)
    public AdminActionLog logRecord(Long actionBy, AdminActionType actionType, String targetId,
                                    String result, String changedValue) {
        AdminActionLog adminActionLog = new AdminActionLog(actionBy, actionType, targetId, result, changedValue);
        return adminActionLogRepository.save(adminActionLog);
    }

    public List<AdminActionLogResponseDTO> search(AdminActionLogRequestDTO request) {
        return adminActionLogRepository.search(
                        request.actionBy(), request.actionType(), request.targetType(), request.targetId(),
                        request.from(), request.to(), request.toPageable())
                .stream()
                .map(AdminActionLogResponseDTO::fromEntity)
                .toList();
    }
}