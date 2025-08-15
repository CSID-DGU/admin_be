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

    private Group createGroupWithSameId(String username, long uidValue) {
        Optional<Group> existing = groupRepository.findById(uidValue);
        if (existing.isPresent()) return existing.get();

        UsedId gidUsedId = usedIdRepository.findById(uidValue)
                .orElseGet(() -> usedIdRepository.saveAndFlush(
                        UsedId.builder().idValue(uidValue).build()
                ));

        Group group = Group.builder()
                .groupName(username)
                .usedId(gidUsedId)
                .build();

        return groupRepository.saveAndFlush(group);
    }
}