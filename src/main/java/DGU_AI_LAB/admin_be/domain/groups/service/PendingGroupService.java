package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestGroupRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 승인 대기 그룹(gid 없는 공유 그룹)의 생애를 맡는다. 그룹은 "새로 만들기" 때 DB 에만 생기고, 그 그룹을 고른 신청의
 * 생성 작업이 성공할 때 config-server 가 발급한 gid 를 받아 인프라 그룹이 된다. 그 그룹을 고른 신청이 하나도 남지
 * 않으면(모두 거절·취소) DB 에서 지운다 — 인프라에는 아무것도 만들어지지 않았다.
 *
 * <p>모든 메서드는 호출자의 트랜잭션 안에서 불러야 한다. 그룹 행 잠금이 직렬화의 기준이다. 교착을 피하려고 잠금
 * 순서는 신청 → 사용자 → 그룹이다 — 신청·사용자 행을 잠근 뒤에 부른다.
 *
 * <p>그룹은 잠근 뒤 DB 에서 다시 읽는다. 같은 트랜잭션에서 그룹을 먼저 읽어 두었다면, 잠그는 조회를 해도 처음 읽은
 * 값(예: gid 없음)이 그대로 남아 그 사이 다른 트랜잭션이 채운 gid 를 보지 못한다 — 그러면 이미 인프라에 만들어진
 * 그룹을 승인 대기로 보고 지울 수 있다. 삭제 쿼리도 gid 가 비어 있을 때만 지우도록 한 번 더 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingGroupService {

    /** 이 상태의 신청은 끝났다 — 승인 대기 그룹을 붙잡지 않는다. */
    private static final Set<Status> FINISHED = EnumSet.of(Status.DENIED, Status.DELETED);

    private final GroupRepository groupRepository;
    private final RequestGroupRepository requestGroupRepository;
    private final EntityManager entityManager;

    /**
     * 승인 직전: 이 신청의 그룹을 잠그고 승인 대기 그룹을 돌려준다. 같은 승인 대기 그룹을 고른 다른 신청이 이미
     * 생성 작업 중(PROCESSING)이면 막는다 — 두 작업이 각자 gid 를 발급한다. 앞 작업이 끝나면 그룹에 gid 가 생겨
     * 이 신청은 평범한 그룹으로 승인된다.
     *
     * @return 이 신청의 그룹 전체(잠근 최신 상태, 번호 순)
     * @throws BusinessException 다른 신청이 같은 승인 대기 그룹을 만드는 중이면 PENDING_GROUP_IN_PROGRESS
     */
    public List<Group> lockForApproval(Request request) {
        List<Group> groups = lockGroupsOf(request);
        List<Long> pendingIds = groups.stream().filter(Group::isPending).map(Group::getGroupId).toList();
        if (pendingIds.isEmpty()) {
            return groups;
        }
        List<Long> busy = requestGroupRepository.findAllByGroupIdsForUpdate(pendingIds).stream()
                .filter(rg -> !Objects.equals(rg.getRequest().getRequestId(), request.getRequestId()))
                .filter(rg -> rg.getRequest().getStatus() == Status.PROCESSING)
                .map(rg -> rg.getRequest().getRequestId())
                .distinct()
                .toList();
        if (!busy.isEmpty()) {
            throw new BusinessException(String.format(
                    "requestId=%d 승인 거절: 같은 승인 대기 그룹을 만드는 신청 %s이(가) PROCESSING",
                    request.getRequestId(), busy), ErrorCode.PENDING_GROUP_IN_PROGRESS);
        }
        return groups;
    }

    /**
     * 생성 작업 성공 결과의 gid 로 이 신청의 승인 대기 그룹을 채운다. 하나라도 채울 수 없으면 아무것도 바꾸지 않고
     * 이유를 돌려준다 — 그대로 확정하면 gid 없는 그룹을 계정에 넣은 것으로 기록하게 된다.
     *
     * @return 문제가 없으면 null, 있으면 관리자에게 알릴 이유
     */
    public String assignFromResult(Request request, List<JobResultResponseDTO.GroupResult> results) {
        List<Group> pending = lockGroupsOf(request).stream().filter(Group::isPending).toList();
        if (pending.isEmpty()) {
            return null;
        }
        Map<String, Long> gidByName = new HashMap<>();
        if (results != null) {
            for (JobResultResponseDTO.GroupResult r : results) {
                if (r != null && r.name() != null) {
                    gidByName.put(r.name(), r.gid());
                }
            }
        }
        List<String> problems = new ArrayList<>();
        for (Group group : pending) {
            Long gid = gidByName.get(group.getGroupName());
            if (gid == null || gid <= 0) {
                problems.add(group.getGroupName() + ": 결과에 gid 없음");
                continue;
            }
            groupRepository.findByUbuntuGid(gid)
                    .filter(other -> !other.getGroupId().equals(group.getGroupId()))
                    .ifPresent(other -> problems.add(String.format(
                            "%s: gid %d 를 이미 그룹 %s 가 씀", group.getGroupName(), gid, other.getGroupName())));
        }
        if (!problems.isEmpty()) {
            return String.join(", ", problems);
        }
        for (Group group : pending) {
            group.assignGid(gidByName.get(group.getGroupName()));
            log.info("[pendingGroup] 그룹 생성 반영: requestId={}, group={}, gid={}",
                    request.getRequestId(), group.getGroupName(), group.getUbuntuGid());
        }
        return null;
    }

    /**
     * 신청이 끝났을 때(거절·취소): 이 신청이 고른 승인 대기 그룹 중, 남은 신청이 없는 그룹을 지운다. 끝나지 않은
     * 신청(대기·처리 중 등)이 하나라도 그 그룹을 고르고 있으면 남긴다. gid 가 있는 그룹은 지우지 않는다.
     */
    public void deleteAbandoned(Request request) {
        List<Long> pendingIds = lockGroupsOf(request).stream()
                .filter(Group::isPending).map(Group::getGroupId).toList();
        if (pendingIds.isEmpty()) {
            return;
        }
        Set<Long> stillUsed = new HashSet<>();
        for (RequestGroup rg : requestGroupRepository.findAllByGroupIdsForUpdate(pendingIds)) {
            if (!FINISHED.contains(rg.getRequest().getStatus())) {
                stillUsed.add(rg.getId().getGroupId());
            }
        }
        for (Long groupId : pendingIds) {
            if (stillUsed.contains(groupId)) {
                continue;
            }
            // 이 신청의 컬렉션에도 남기지 않는다 — 지운 그룹을 가리키는 객체가 남아 있으면 이후 저장 때 되살아난다.
            request.removeGroup(groupId);
            requestGroupRepository.deleteAllOfPendingGroup(groupId);
            int deleted = groupRepository.deletePendingById(groupId);
            log.info("[pendingGroup] 고른 신청이 모두 끝나 승인 대기 그룹 삭제: requestId={}, groupId={}, deleted={}",
                    request.getRequestId(), groupId, deleted);
        }
    }

    /** 이 신청의 그룹을 번호 순으로 잠그고 최신 값으로 읽는다. 그룹 번호는 신청 기록의 키에서 꺼낸다. */
    private List<Group> lockGroupsOf(Request request) {
        List<Long> ids = request.getRequestGroups().stream()
                .map(RequestGroup::getId)
                .filter(Objects::nonNull)
                .map(id -> id.getGroupId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Group> groups = groupRepository.findAllByIdForUpdate(ids);
        // 잠그는 다시 읽기는 트랜잭션 시작 시점의 스냅숏이 아니라 지금 커밋된 값을 읽는다.
        groups.forEach(g -> entityManager.refresh(g, LockModeType.PESSIMISTIC_WRITE));
        return groups;
    }
}
