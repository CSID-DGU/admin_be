package DGU_AI_LAB.admin_be.domain.usedIds.service;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.UsedId;
import DGU_AI_LAB.admin_be.domain.usedIds.repository.UsedIdRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdAllocationService {

    private static final long UID_BASE = 10_000L;
    private static final int MAX_RETRY = 5;
    private static final long GID_BASE = 2_000L;
    private static final long GID_MAX_VALUE = 65535L;

    private final UsedIdRepository usedIdRepository;
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
                .orElseGet(() -> createGroupWithSameId(username, uid.getIdValue()));

        return new AllocationResult(uid, primaryGroup);
    }

    private Optional<UsedId> findReusableUidByUbuntuUsername(String ubuntuUsername) {
        return requestRepository
                .findTopByUbuntuUsernameAndUbuntuUidIsNotNullOrderByApprovedAtDesc(ubuntuUsername)
                .map(Request::getUbuntuUid);
    }

    // 새 UID 생성
    private UsedId allocateNewUid() {
        for (int i = 0; i < MAX_RETRY; i++) {
            long currentMax = usedIdRepository.findMaxIdValue().orElse(UID_BASE - 1L);
            long candidate = Math.max(UID_BASE, currentMax + 1);
            try {
                return usedIdRepository.saveAndFlush(UsedId.builder().idValue(candidate).build());
            } catch (DataIntegrityViolationException ignore) {}
        }
        throw new BusinessException(ErrorCode.UID_ALLOCATION_FAILED);
    }

    /**
     * 새로운 GID를 할당하고 UsedId 테이블에 저장합니다.
     */
    @Transactional
    public Long allocateNewGid() {
        for (int i = 0; i < MAX_RETRY; i++) {
            // GID 범위 내에서 최대값을 찾습니다.
            Long currentMax = usedIdRepository.findMaxIdValueInRange(GID_BASE, GID_MAX_VALUE)
                    .orElse(GID_BASE - 1L);

            long candidate = Math.max(GID_BASE, currentMax + 1);

            if (candidate > GID_MAX_VALUE) {
                throw new BusinessException(ErrorCode.GID_ALLOCATION_FAILED);
            }

            try {
                usedIdRepository.saveAndFlush(UsedId.builder().idValue(candidate).build());
                return candidate;
            } catch (DataIntegrityViolationException ignore) {}
        }
        throw new BusinessException(ErrorCode.GID_ALLOCATION_FAILED);
    }


    private Group createGroupWithSameId(String username, long uidValue) {
        Optional<Group> existing = groupRepository.findById(uidValue);
        if (existing.isPresent()) return existing.get();

        UsedId gidUsedId = usedIdRepository.findById(uidValue)
                .orElseGet(() -> usedIdRepository.saveAndFlush(
                        UsedId.builder().idValue(uidValue).build()
                ));

        Group group = Group.builder()
                .groupName(username)
                .ubuntuGid(uidValue)
                .usedId(gidUsedId)
                .build();

        return groupRepository.saveAndFlush(group);
    }
}