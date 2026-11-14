package DGU_AI_LAB.admin_be.domain.AdminActionLog.service;

import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminActionType;
import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionLog;
import DGU_AI_LAB.admin_be.domain.adminActionLog.repository.AdminActionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)

public class AdminActionLogService {

    private final AdminActionLogRepository adminActionLogRepository;

    @Transactional
    public AdminActionLog logRecord(Long actionBy, AdminActionType actionType, String targetId, String result, String changedValue) {
        AdminActionLog adminActionLog = new AdminActionLog(actionBy,actionType,targetId,result,changedValue);
        return adminActionLogRepository.save(adminActionLog);

    }


}