package DGU_AI_LAB.admin_be.domain.adminActionLog.service;

import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionLog;
import DGU_AI_LAB.admin_be.domain.adminActionLog.repository.AdminActionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기록 한 줄을 자기 트랜잭션으로 저장한다. 작업 쪽 트랜잭션은 이미 끝났으므로 그 결과에 영향을 주지 않고,
 * DB 가 느릴 때 응답을 오래 붙잡지 않도록 짧은 제한 시간을 둔다.
 */
@Component
@RequiredArgsConstructor
public class AdminActionLogWriter {

    static final int TIMEOUT_SECONDS = 3;

    private final AdminActionLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = TIMEOUT_SECONDS)
    public void write(AdminActionLog log) {
        repository.save(log);
    }
}
