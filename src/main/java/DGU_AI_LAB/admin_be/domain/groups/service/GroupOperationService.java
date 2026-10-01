package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.groups.dto.request.CreateGroupRequestDTO;
import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupOperationResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupOperation;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupOperationKind;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupOperationStatus;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupOperationRepository;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.GroupChangeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 공용 그룹 작업(생성·멤버 추가·제거)의 진행을 맡는다: 등록 → config-server 작업 → 결과 반영.
 *
 * <p>그룹은 AD·계정 원장·팀 디렉터리·떠 있는 컨테이너에 걸쳐 있다. 요청을 받으면 작업으로 등록만 하고 돌아오고,
 * 결과는 GroupOperationJobPoller 가 {@link #complete}·{@link #fail}로 반영한다. DB(groups·user_groups)는 작업이
 * 성공한 뒤에만 바꾼다 — 먼저 바꾸면 화면에는 반영됐다고 나오는데 실제 권한은 그대로다.
 *
 * <p>작업 등록은 트랜잭션 안에서 한다. PROCESSING 이 보이는 시점에는 그 작업이 이미 등록돼 있어, 폴러가 등록 전의
 * 작업을 "기록 없음"으로 읽어 실패로 닫지 않는다. 등록이 실패하면 아무것도 남지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupOperationService {

    /** 작업은 성공했는데 DB 에 반영할 수 없을 때 남기는 오류 코드. config-server 의 error_code 와 같은 칸에 들어간다. */
    static final String ERROR_GID_MISSING = "GID_MISSING";
    static final String ERROR_DUPLICATE_GROUP = "DUPLICATE_GROUP";
    static final String ERROR_ACCOUNT_CHANGED = "ACCOUNT_CHANGED";
    private static final int ERROR_CODE_MAX_LENGTH = 64;

    private final GroupOperationRepository operationRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final JobClient jobClient;
    private final GroupCreateThrottle groupCreateThrottle;
    private final AlarmService alarmService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    /** 작업이 끝난 뒤(트랜잭션 밖에서) 보낼 안내. */
    private record AddedNotice(ChangeRequest changeRequest, List<String> groupNames) {}

    private record FailureNotice(Long changeRequestId, String serverName, String username) {}

    /**
     * 그룹을 만드는 작업을 등록한다. 그룹 번호(gid)는 작업이 정하므로 groups 행은 작업이 성공한 뒤에 생긴다.
     *
     * @throws BusinessException 이름이 이미 쓰이거나(409), 같은 이름을 만드는 중이거나(409), 하루 한도를 넘은 경우(429)
     */
    public GroupOperationResponseDTO requestCreate(CreateGroupRequestDTO dto, Long userId) {
        String member = StringUtils.hasText(dto.ubuntuUsername()) ? dto.ubuntuUsername() : null;
        GroupOperationResponseDTO response = inTransaction(() -> {
            if (member != null && !requestRepository.existsByUser_UbuntuUsernameAndUser_UserId(member, userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN_REQUEST);
            }
            if (groupRepository.existsByGroupName(dto.groupName())) {
                throw new BusinessException(ErrorCode.DUPLICATE_GROUP_NAME);
            }
            // config-server는 원장에 계정이 생긴 이름만 막는다 — 계정명만 등록하고 아직 승인 전인
            // 사용자와 같은 이름의 그룹을 만들면 그 사용자의 계정 생성이 나중에 실패한다.
            if (userRepository.existsByUbuntuUsername(dto.groupName())) {
                throw new BusinessException(ErrorCode.GROUP_NAME_CONFLICTS_USER);
            }
            if (operationRepository.existsByKindAndGroupNameAndStatus(
                    GroupOperationKind.CREATE, dto.groupName(), GroupOperationStatus.PROCESSING)) {
                throw new BusinessException(ErrorCode.GROUP_OPERATION_IN_PROGRESS);
            }
            // 검증을 통과한 요청만 센다 — 이름 중복 같은 실패로 한도를 쓰지 않게 한다.
            groupCreateThrottle.acquire(userId);

            User requester = userRepository.findById(userId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
            GroupOperation operation = operationRepository.save(GroupOperation.create(requester, dto.groupName(), member));
            operation.registered(jobClient.registerGroupChange(GroupChangeRegisterRequestDTO.create(
                    operation.getGroupOperationId(), requester.getUbuntuUsername(), dto.groupName(),
                    member == null ? List.of() : List.of(member))));
            return GroupOperationResponseDTO.of(operation, null);
        });
        log.info("[groupOperation] operationId={} 그룹 생성 등록: groupName={}", response.operationId(), dto.groupName());
        return response;
    }

    /**
     * 공유 그룹 추가 변경 요청을 승인해 작업으로 등록하고, 변경 요청을 반영 중(PROCESSING)으로 둔다.
     * 호출자의 트랜잭션 안에서 불러야 한다 — 변경 요청과 원본 신청을 잠그고 검증한 그 트랜잭션이다.
     */
    public void startAdd(ChangeRequest changeRequest, Request originalRequest, User admin, String adminComment) {
        List<String> groupNames = groupsOf(changeRequest).stream().map(Group::getGroupName).toList();
        GroupOperation operation = operationRepository.save(GroupOperation.add(
                changeRequest, originalRequest.getUser(), originalRequest.getUbuntuUsername(), admin));
        operation.registered(jobClient.registerGroupChange(GroupChangeRegisterRequestDTO.add(
                operation.getGroupOperationId(), originalRequest.getUbuntuUsername(), groupNames)));
        changeRequest.startProcessing(admin, adminComment);
        log.info("[groupOperation] operationId={} 그룹 추가 등록: changeRequestId={}, groups={}",
                operation.getGroupOperationId(), changeRequest.getChangeRequestId(), groupNames);
    }

    /**
     * 계정을 공용 그룹에서 빼는 작업을 등록한다. 리눅스 계정명을 정한 적이 없는 사용자는 AD 에 반영된 멤버십이
     * 없으므로 작업 없이 DB 만 정리한다. 팀 디렉터리와 그 안의 파일은 건드리지 않는다.
     */
    public GroupOperationResponseDTO requestRemove(Long userId, Long groupId, Long adminId) {
        GroupOperationResponseDTO response = inTransaction(() -> {
            User user = userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
            Group group = groupRepository.findById(groupId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.GROUP_NOT_FOUND));
            if (user.getUbuntuUsername() == null) {
                removeFromDb(user, groupId);
                return GroupOperationResponseDTO.appliedWithoutJob(GroupOperationKind.REMOVE.name(), group.getGroupName());
            }
            if (operationRepository.existsByKindAndUser_UserIdAndGroup_GroupIdAndStatus(
                    GroupOperationKind.REMOVE, userId, groupId, GroupOperationStatus.PROCESSING)) {
                throw new BusinessException(ErrorCode.GROUP_OPERATION_IN_PROGRESS);
            }
            GroupOperation operation = operationRepository.save(
                    GroupOperation.remove(user, group, userRepository.getReferenceById(adminId)));
            operation.registered(jobClient.registerGroupChange(GroupChangeRegisterRequestDTO.remove(
                    operation.getGroupOperationId(), user.getUbuntuUsername(), group.getGroupName())));
            return GroupOperationResponseDTO.of(operation, null);
        });
        log.info("[groupOperation] operationId={} 그룹 제거 요청: userId={}, groupId={}, status={}",
                response.operationId(), userId, groupId, response.status());
        return response;
    }

    /** 작업이 성공했다. 결과를 DB 에 반영한다. 이미 끝난 작업이면 아무것도 하지 않는다. */
    public void complete(Long operationId, JobResultResponseDTO.Result result) {
        AddedNotice notice = inTransaction(() -> {
            GroupOperation operation = lock(operationId);
            if (!operation.isProcessing()) {
                return null;
            }
            return switch (operation.getKind()) {
                case CREATE -> {
                    applyCreate(operation, result);
                    yield null;
                }
                case ADD -> applyAdd(operation);
                case REMOVE -> {
                    removeFromDb(lockUser(operation.getUser().getUserId()), operation.getGroup().getGroupId());
                    operation.markApplied();
                    yield null;
                }
            };
        });
        log.info("[groupOperation] operationId={} 작업 성공 반영", operationId);
        if (notice != null) {
            notifyAdded(notice);
        }
    }

    /**
     * 작업이 성공하지 못했다(실패·결과 불명·기록 없음). 이미 맞춰진 조각은 그대로 남지만 같은 요청을 다시 내면
     * 이어서 끝나므로, 그룹 추가는 변경 요청을 승인 대기로 되돌려 다시 승인하거나 거절할 수 있게 한다.
     */
    public void fail(Long operationId, String errorCode) {
        FailureNotice notice = inTransaction(() -> {
            GroupOperation operation = lock(operationId);
            if (!operation.isProcessing()) {
                return null;
            }
            operation.markFailed(truncate(errorCode));
            if (operation.getKind() != GroupOperationKind.ADD) {
                return null;
            }
            return returnChangeRequestToPending(operation);
        });
        log.error("[groupOperation] operationId={} 작업 실패: error={}", operationId, errorCode);
        if (notice != null) {
            notifyAddFailed(notice, errorCode);
        }
    }

    /** 작업의 진행 상태. 요청한 사람·그룹이 바뀌는 계정의 주인·관리자만 볼 수 있다. */
    public GroupOperationResponseDTO get(Long operationId, Long viewerId, boolean viewerIsAdmin) {
        return inTransaction(() -> {
            GroupOperation operation = operationRepository.findById(operationId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.GROUP_OPERATION_NOT_FOUND));
            boolean involved = Objects.equals(operation.getUser().getUserId(), viewerId)
                    || Objects.equals(operation.getRequestedBy().getUserId(), viewerId);
            if (!viewerIsAdmin && !involved) {
                // 남의 작업은 있는지조차 알리지 않는다.
                throw new EntityNotFoundException(ErrorCode.GROUP_OPERATION_NOT_FOUND);
            }
            Group created = operation.getKind() == GroupOperationKind.CREATE
                    && operation.getStatus() == GroupOperationStatus.APPLIED
                    ? groupRepository.findByGroupName(operation.getGroupName()).orElse(null)
                    : null;
            return GroupOperationResponseDTO.of(operation, created);
        });
    }

    private void applyCreate(GroupOperation operation, JobResultResponseDTO.Result result) {
        Long gid = result == null ? null : result.gid();
        if (gid == null || gid <= 0) {
            log.error("[groupOperation] operationId={} 그룹은 만들어졌으나 결과에 gid 가 없음 — 같은 이름으로 다시 만들면 이어진다",
                    operation.getGroupOperationId());
            operation.markFailed(ERROR_GID_MISSING);
            return;
        }
        if (groupRepository.existsByGroupName(operation.getGroupName()) || groupRepository.existsByUbuntuGid(gid)) {
            // 같은 이름을 동시에 만든 다른 요청이 먼저 저장했다. 인프라의 그룹은 그 요청의 것과 같은 하나다.
            operation.markFailed(ERROR_DUPLICATE_GROUP);
            return;
        }
        groupRepository.save(Group.builder().groupName(operation.getGroupName()).ubuntuGid(gid).build());
        operation.markApplied();
    }

    private AddedNotice applyAdd(GroupOperation operation) {
        ChangeRequest changeRequest = lockChangeRequest(operation);
        User owner = lockUser(operation.getUser().getUserId());
        if (!Objects.equals(owner.getUbuntuUsername(), operation.getUbuntuUsername())) {
            // 작업이 도는 사이 계정이 회수됐다. 그룹은 사라진 계정에 더해진 것이라 기록하지 않는다.
            operation.markFailed(ERROR_ACCOUNT_CHANGED);
            changeRequest.returnToPending();
            return null;
        }
        // 계정(User) 단위로 누적한다 — 같은 계정의 다른 컨테이너에도 이 그룹이 반영된 것으로 보여야 한다.
        Set<Group> groups = groupsOf(changeRequest);
        groups.forEach(owner::addGroupIfAbsent);
        changeRequest.completeProcessing();
        operation.markApplied();
        // 트랜잭션 종료 후(안내 발송 시점) 사용되는 지연 로딩 필드를 미리 초기화
        changeRequest.getRequestedBy().getEmail();
        return new AddedNotice(changeRequest, groups.stream().map(Group::getGroupName).sorted().toList());
    }

    private FailureNotice returnChangeRequestToPending(GroupOperation operation) {
        ChangeRequest changeRequest = lockChangeRequest(operation);
        if (changeRequest.getStatus() != Status.PROCESSING) {
            return null;
        }
        changeRequest.returnToPending();
        return new FailureNotice(changeRequest.getChangeRequestId(),
                changeRequest.getRequest().getResourceGroup().getServerName(), operation.getUbuntuUsername());
    }

    private void removeFromDb(User user, Long groupId) {
        user.removeGroup(groupId);
        // 다시 만들 수 있는 컨테이너만 본다. 대기 중인 신청은 아직 승인 판단 전이라 그대로 둔다.
        for (Request request : requestRepository.findAllByUser_UserIdAndStatusIn(user.getUserId(), Status.activeStatuses())) {
            request.removeGroup(groupId);
        }
    }

    /** 변경 요청이 더하려는 그룹들(new_value 의 gid 목록). */
    private Set<Group> groupsOf(ChangeRequest changeRequest) {
        Set<Long> gids;
        try {
            gids = objectMapper.readValue(changeRequest.getNewValue(),
                    objectMapper.getTypeFactory().constructCollectionType(Set.class, Long.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to parse change request value: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        Set<Group> groups = Set.copyOf(groupRepository.findAllByUbuntuGidIn(gids));
        if (groups.size() != gids.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return groups;
    }

    private void notifyAdded(AddedNotice notice) {
        try {
            ChangeRequest changeRequest = notice.changeRequest();
            alarmService.sendGroupAddedEmail(changeRequest, changeRequest.getAdminComment(), notice.groupNames());
        } catch (Exception e) {
            log.warn("그룹 추가 승인 안내 메일 발송 실패: changeRequestId={}",
                    notice.changeRequest().getChangeRequestId(), e);
        }
    }

    // 관리자가 실제로 보는 farm/lab 채널로 알린다. 알림 실패가 결과 반영을 막으면 안 된다.
    private void notifyAddFailed(FailureNotice notice, String errorCode) {
        try {
            alarmService.sendAdminSlackNotification(notice.serverName(), String.format(
                    "[그룹 추가] 반영하지 못해 변경 요청을 승인 대기로 되돌렸습니다 - 다시 승인하면 이어서 반영됩니다: "
                            + "changeRequestId=%d, username=%s, error=%s",
                    notice.changeRequestId(), notice.username(), errorCode));
        } catch (Exception ignored) {
        }
    }

    private GroupOperation lock(Long operationId) {
        return operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.GROUP_OPERATION_NOT_FOUND));
    }

    private ChangeRequest lockChangeRequest(GroupOperation operation) {
        return changeRequestRepository.findByIdForUpdate(operation.getChangeRequest().getChangeRequestId())
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private User lockUser(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    private static String truncate(String errorCode) {
        return errorCode != null && errorCode.length() > ERROR_CODE_MAX_LENGTH
                ? errorCode.substring(0, ERROR_CODE_MAX_LENGTH) : errorCode;
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
