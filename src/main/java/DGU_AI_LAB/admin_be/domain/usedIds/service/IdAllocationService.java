package DGU_AI_LAB.admin_be.domain.usedIds.service;

import DGU_AI_LAB.admin_be.domain.usedIds.entity.CounterKey;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.IdCounter;
import DGU_AI_LAB.admin_be.domain.usedIds.repository.IdCounterRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class IdAllocationService {

    private final IdCounterRepository idCounterRepository;

    /**
     * 새로운 GID를 할당합니다.
     * IdCounter에 비관적 락을 적용하여 동시성을 보장합니다.
     */
    @Transactional
    public Long allocateNewGid() {
        IdCounter counter = idCounterRepository.findByKey(CounterKey.SHARED_GID)
                .orElseThrow(() -> new BusinessException(ErrorCode.GID_ALLOCATION_FAILED));

        long gid = counter.allocateOne();
        idCounterRepository.saveAndFlush(counter);

        return gid;
    }
}
