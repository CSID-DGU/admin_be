package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.dto.ChangeRequestNotice;
import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.global.util.AfterCommit;
import DGU_AI_LAB.admin_be.domain.home.service.HomeCleanupService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.groups.service.PendingGroupService;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.SingleChangeRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ChangeRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.SaveRequestRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.portRequests.service.PortRequestService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RequestCommandService {

    private final ObjectMapper objectMapper;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ContainerImageRepository containerImageRepository;
    private final GroupRepository groupRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final ChangeRequestDescriber changeRequestDescriber;
    private final PortRequestService portRequestService;
    private final AlarmService alarmService;
    private final RequestCreateThrottle requestCreateThrottle;
    private final PendingGroupService pendingGroupService;
    private final HomeCleanupService homeCleanupService;

    /** 아직 승인·거절이 정해지지 않은 신청 상태. */
    private static final List<Status> AWAITING_DECISION = List.of(Status.PENDING, Status.PROCESSING);

    /**
     * 사용자가 자신의 대기 중(PENDING) 또는 거절된(DENIED) 신청을 취소한다.
     * FULFILLED/MIGRATING 상태는 Request.delete()가 자체적으로 거부한다 — 실행 중인
     * 컨테이너가 있는 신청은 인프라 정리 없이 그냥 지울 수 없기 때문.
     */
    @Transactional
    public void cancelRequest(Long userId, Long requestId) {
        // 행 잠금 조회: delete()의 PROCESSING 가드는 로드 시점의 엔티티 상태를 보므로,
        // 잠그지 않으면 승인이 PENDING -> PROCESSING을 커밋하는 사이에 읽은 낡은 PENDING을
        // 근거로 통과해 DELETED로 덮어쓴다. 그러면 승인 후처리가 상태 재확인에서 실패해
        // 불필요한 보상 트랜잭션과 관리자 알림이 발생한다.
        Request request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!request.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_REQUEST);
        }

        // 승인을 기다리던 신청만 신청서 채널에 취소를 알린다. 거절된 신청은 이미 처리가 끝나 알릴 것이 없다.
        boolean awaitingDecision = request.getStatus() == Status.PENDING;
        request.delete();
        // 이 신청이 승인 대기 그룹을 고른 마지막 신청이었다면 그 그룹도 지운다 — 인프라에는 아직 없다.
        pendingGroupService.deleteAbandoned(request);

        if (awaitingDecision) {
            notifyCancelledAfterCommit(request);
        }
    }

    /** 문구는 지금(트랜잭션 안에서) 만들고 전송만 커밋 뒤로 미룬다. 알림 실패는 취소를 실패시키지 않는다. */
    private void notifyCancelledAfterCommit(Request request) {
        Runnable send;
        try {
            send = alarmService.prepareRequestCancelledNotification(request);
        } catch (Exception e) {
            log.error("신청 취소 알림을 만들지 못했습니다. (요청 ID: {}). 하지만 취소는 정상적으로 처리되었습니다.", request.getRequestId(), e);
            return;
        }
        AfterCommit.run("신청 취소 알림, 요청 ID " + request.getRequestId(), send);
    }

    /**
     * 단일 변경 요청 생성 - DTO가 모든 검증을 담당하므로 서비스는 단순히 처리만 함
     */
    @Transactional
    public ChangeRequestResponseDTO createSingleChangeRequest(Long userId, SingleChangeRequestDTO dto) {
        Long requestId = dto.requestId();
        // 행 잠금 조회: 같은 신청에 동시에 들어온 변경 요청이 아래 대기 중 중복 검사를 함께 통과하지 못하게 한다.
        Request originalRequest = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 요청자가 원본 요청의 소유자인지 확인
        if (!originalRequest.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_REQUEST);
        }

        // FULFILLED 상태에서만 변경 요청 가능
        if (originalRequest.getStatus() != Status.FULFILLED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }

        // 같은 종류는 한 번에 하나만 대기시킨다 — 무제한으로 쌓이면 관리자가 무엇을 승인해야 할지 알 수 없다.
        if (changeRequestRepository.existsByRequest_RequestIdAndChangeTypeAndStatus(
                requestId, dto.changeType(), Status.PENDING)) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_ALREADY_PENDING);
        }

        User requestedBy = originalRequest.getUser();

        // DTO 팩토리 메서드를 사용하여 검증된 ChangeRequest 생성 및 저장
        ChangeRequest changeRequest = SingleChangeRequestDTO.createValidatedChangeRequest(
                dto, originalRequest, requestedBy, portRequestService.getPortRequestsByRequestId(requestId), objectMapper);
        changeRequestRepository.save(changeRequest);

        notifyChangeRequestedAfterCommit(changeRequest);
        return ChangeRequestResponseDTO.fromEntity(changeRequest);
    }

    /**
     * 변경 요청이 들어왔음을 알린다. 값은 지금(트랜잭션 안에서) 읽고 전송만 커밋 뒤로 미룬다.
     * 알림 실패는 접수를 실패시키지 않는다.
     */
    private void notifyChangeRequestedAfterCommit(ChangeRequest changeRequest) {
        ChangeRequestNotice notice = ChangeRequestNotice.of(changeRequest, changeRequestDescriber.describe(changeRequest));
        AfterCommit.run("변경 요청 접수 알림, changeRequestId " + changeRequest.getChangeRequestId(),
                () -> alarmService.sendChangeRequestNotification(notice));
    }

    /**
     * 신청서가 고른 그룹. groupIds(새 방식)는 승인 대기 그룹도 가리킬 수 있다 — 그 그룹을 고른 마지막 신청이
     * 거절·취소되면 그룹이 지워지므로(PendingGroupService), 지우는 쪽과 엇갈리지 않게 그룹 행을 잠그고 읽는다.
     * ubuntuGids(예전 방식)는 gid 가 있는 그룹만 가리키고, 그런 그룹은 지워지지 않는다.
     */
    private List<Group> findChosenGroups(SaveRequestRequestDTO dto) {
        boolean byId = dto.groupIds() != null && !dto.groupIds().isEmpty();
        boolean byGid = dto.ubuntuGids() != null && !dto.ubuntuGids().isEmpty();
        if (byId && byGid) {
            throw new BusinessException("groupIds와 ubuntuGids 중 하나만 보내 주세요.", ErrorCode.INVALID_INPUT_VALUE);
        }
        List<Group> groups;
        int expected;
        if (byId) {
            groups = groupRepository.findAllByIdForUpdate(dto.groupIds());
            expected = dto.groupIds().size();
        } else if (byGid) {
            groups = groupRepository.findAllByUbuntuGidIn(dto.ubuntuGids());
            expected = dto.ubuntuGids().size();
        } else {
            return List.of();
        }
        if (groups.size() != expected) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return groups;
    }

    /** 신청 생성 */
    @Transactional
    public SaveRequestResponseDTO createRequest(Long userId, SaveRequestRequestDTO dto) {
        // 행 잠금 조회: 계정 배정(승인 시점)이 User 행 잠금으로 직렬화되므로, 신청 생성
        // 자체도 같은 사용자 기준으로 일관되게 잠그고 시작한다.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ResourceGroup rg = resourceGroupRepository.findById(dto.resourceGroupId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 유저네임은 신청마다 고르는 값이 아니라 가입 시 정해진 웹 계정의 고정값이다.
        String ubuntuUsername = user.getUbuntuUsername();
        if (ubuntuUsername == null || ubuntuUsername.isBlank()) {
            throw new BusinessException(ErrorCode.UBUNTU_USERNAME_NOT_ASSIGNED);
        }

        // 승인을 기다리는 신청은 사용자당 하나만 받는다 — 같은 신청이 여러 건 쌓이면 관리자가 무엇을 승인해야 할지
        // 알 수 없다. 바꾸고 싶으면 기존 신청을 취소하고 다시 낸다. 처리 중(PROCESSING)도 막는다: 생성 작업이
        // 실패하면 PENDING으로 돌아오므로, 그 사이에 받은 새 신청과 함께 대기가 두 건이 된다.
        // 사용 중인 컨테이너는 세지 않는다 — Pod 생성/상태조회가 requestId로 구분되므로 한 사용자가 컨테이너를
        // 여러 개 가질 수 있다. (마이그레이션은 아직 username 기준으로 "그 유저의 pod"를 찾으므로, 사용자가
        // Pod를 2개 이상 가진 상태에서 마이그레이션하면 대상이 모호해질 수 있는 게 알려진 제약이다.)
        // 위에서 User 행을 잠갔으므로 같은 사용자의 동시 신청 두 건이 이 검사를 함께 통과하지 못한다.
        if (requestRepository.existsByUser_UserIdAndStatusIn(userId, AWAITING_DECISION)) {
            throw new BusinessException(ErrorCode.CONTAINER_REQUEST_ALREADY_PENDING);
        }

        // 홈 삭제 시도도 User 행을 잠그고 만들어지므로(HomeCleanupService.begin), 여기서 진행 중이 아니면
        // 이 신청이 커밋된 뒤에는 삭제가 시작되지 않는다.
        if (homeCleanupService.isDeleting(userId)) {
            throw new BusinessException(ErrorCode.HOME_CLEANUP_IN_PROGRESS);
        }

        ContainerImage img = containerImageRepository.findById(dto.imageId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // SSH 비밀번호는 웹 계정 비밀번호 하나다. 해시는 가입·로그인·비밀번호 변경 때 만들어지므로, 없다면
        // 그 전에 발급된 로그인 세션이다 — 다시 로그인하면 채워진다.
        if (!user.hasUbuntuPassword()) {
            throw new BusinessException(ErrorCode.UBUNTU_PASSWORD_REQUIRED);
        }

        List<Group> groups = findChosenGroups(dto);

        // 입력이 잘못돼 거절된 요청은 하루 한도에서 빼려고 모든 검증 뒤에 센다. Redis 카운터는 트랜잭션과 함께
        // 되돌아가지 않으므로, 이 아래에는 입력 때문에 실패하는 검증을 두지 않는다.
        requestCreateThrottle.acquire(userId);

        // addGroup()/포트 신청이 requestId를 요구하므로 여기서 즉시 flush해 ID를 확보한다.
        Request req = requestRepository.saveAndFlush(dto.toEntity(user, rg, img));

        for (Group g : groups) {
            req.addGroup(g);
        }

        // === 포트 추가 신청 ===
        List<PortRequests> portRequests = new ArrayList<>();
        if (dto.portRequests() != null && !dto.portRequests().isEmpty()) {
            for (var portRequestDTO : dto.portRequests()) {
                portRequests.add(portRequestService.createPortRequest(
                        req,
                        rg,
                        portRequestDTO.internalPort(),
                        portRequestDTO.usagePurpose()
                ));
            }
        }

        // === 관리자 채널 알림 ===
        // 커밋 후에 보낸다: Redis 장애 시 직접 HTTP 전송으로 폴백하는데, 그게 User 행 잠금을 쥔 채
        // 실행되면 같은 사용자의 승인·신청이 그만큼 막힌다. 롤백되면 존재하지 않는 신청을 알리지도 않는다.
        AfterCommit.run("새 신청 알림, 요청 ID " + req.getRequestId(), () -> notifyNewRequest(req, portRequests, userId));

        return SaveRequestResponseDTO.fromEntity(req);
    }

    private void notifyNewRequest(Request req, List<PortRequests> portRequests, Long userId) {
        log.info("새로운 사용 신청에 대한 슬랙 알림을 전송합니다. 요청 ID: {}", req.getRequestId());
        long activeContainers = requestRepository.countByUser_UserIdAndStatusIn(userId, Status.activeStatuses());
        alarmService.sendNewRequestNotification(req, portRequests, activeContainers);
    }
}
