package DGU_AI_LAB.admin_be.domain.usedIds.service;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.CounterKey;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.IdCounter;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.UsedId;
import DGU_AI_LAB.admin_be.domain.usedIds.repository.IdCounterRepository;
import DGU_AI_LAB.admin_be.domain.usedIds.repository.UsedIdRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class IdAllocationService {

    private final UsedIdRepository usedIdRepository;
    private final IdCounterRepository idCounterRepository;
    private final GroupRepository groupRepository;
    private final RequestRepository requestRepository;

    @Getter
    @AllArgsConstructor
    public static class AllocationResult {
        private final UsedId uid;
        private final Group primaryGroup;
    }

    @Transactional
    public AllocationResult allocateFor(Request request) {
        final String username = request.getUbuntuUsername();

        UsedId uid = findReusableUidByUbuntuUsername(username)
                .orElseGet(this::allocateNewUid);

        Group primaryGroup = groupRepository.findById(uid.getIdValue())
                .orElseGet(() -> createPrimaryGroup(username, uid.getIdValue()));

        return new AllocationResult(uid, primaryGroup);
    }

    private Optional<UsedId> findReusableUidByUbuntuUsername(String ubuntuUsername) {
        return requestRepository
                .findTopByUbuntuUsernameAndUbuntuUidIsNotNullOrderByApprovedAtDesc(ubuntuUsername)
                .map(Request::getUbuntuUid);
    }

    private UsedId allocateNewUid() {
        IdCounter counter = idCounterRepository.findByKey(CounterKey.UID)
                .orElseThrow(() -> new BusinessException(ErrorCode.UID_ALLOCATION_FAILED));
        long uid = counter.allocateOne();
        idCounterRepository.saveAndFlush(counter);
        return usedIdRepository.saveAndFlush(UsedId.builder().idValue(uid).build());
    }

    /**
     * 새로운 GID를 할당하고 UsedId 테이블에 저장합니다.
     * IdCounter에 비관적 락을 적용하여 동시성을 보장합니다.
     */
    @Transactional
    public Long allocateNewGid() {
        IdCounter counter = idCounterRepository.findByKey(CounterKey.SHARED_GID)
                .orElseThrow(() -> new BusinessException(ErrorCode.GID_ALLOCATION_FAILED));

        long gid = counter.allocateOne(); // 범위 초과 시 NO_AVAILABLE_RESOURCES 예외 발생
        idCounterRepository.saveAndFlush(counter);

        try {
            usedIdRepository.saveAndFlush(UsedId.builder().idValue(gid).build());
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.GID_ALLOCATION_FAILED);
        }

        return gid;
    }

    private Group createPrimaryGroup(String username, long uidValue) {
        return groupRepository.saveAndFlush(
                Group.builder()
                        .groupName(username)
                        .ubuntuGid(uidValue)
                        .build()
        );
    }

    /**
     * 사용 완료된 UsedId를 DB에서 삭제하여 반환합니다.
     * 스케줄러의 트랜잭션에 참여합니다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseId(UsedId usedId) {
        if (usedId == null) {
            log.warn("반환할 UsedId가 null입니다. 작업을 건너뜁니다.");
            return;
        }

        try {
            usedIdRepository.delete(usedId);
            log.info("UsedId 반환 (삭제) 성공: {}", usedId.getIdValue());
        } catch (Exception e) {
            log.error("UsedId 반환 (삭제) 실패: {}", usedId.getIdValue(), e);
            throw new BusinessException("UsedId 반환 실패", ErrorCode.USED_ID_RELEASE_FAILED);
        }
    }
}
