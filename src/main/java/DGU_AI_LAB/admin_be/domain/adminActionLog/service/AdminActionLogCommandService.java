package DGU_AI_LAB.admin_be.domain.adminActionLog.service;

import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.AdminActionEvent;
import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionLog;
import DGU_AI_LAB.admin_be.domain.adminActionLog.repository.AdminActionLogRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 3)
public class AdminActionLogCommandService {
    private final AdminActionLogRepository repository;

    public void save(AdminActionEvent event) {
        if (!repository.existsByEventId(event.eventId())) {
            repository.saveAndFlush(AdminActionLog.from(event));
        }
    }
}
