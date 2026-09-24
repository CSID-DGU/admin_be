package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupService;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.service.PortRequestService;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.PortRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ProvisionRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.UserCreationRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminRequestCommandService {

    private final AlarmService alarmService;

    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ContainerImageRepository containerImageRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final GroupRepository groupRepository;
    private final GroupService groupService;
    private final PodExternalPortRepository podExternalPortRepository;
    private final OperationJobService operationJobService;
    private final PortRequestService portRequestService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    // 계정 존재 확인~생성~UID 커밋 구간을 userId별로 직렬화한다. 이 구간은 짧은 DB
    // 트랜잭션 두 개(확인용/커밋용) 사이에 config-server HTTP 호출이 끼어 있어, User 행을
    // 커밋 시점에만 잠그는 것으로는 안 막힌다 — 확인 시점에 잠갔다 바로 풀면, 그 직후
    // 같은 사용자의 다른 신청을 승인하는 스레드가 잠금 해제 틈을 비집고 들어와 똑같이
    // "계정 없음"을 보고 계정 생성 API를 중복 호출할 수 있다(치명적이진 않다 — 한쪽은
    // config-server 409로 실패해 PENDING으로 되돌아가고 재시도하면 정상적으로 재사용하지만,
    // 관리자 입장에선 불필요한 승인 실패로 보인다). admin_be가 단일 인스턴스로만 배포되므로
    // in-process 락으로 충분하다.
    private final ConcurrentHashMap<Long, Object> userApprovalLocks = new ConcurrentHashMap<>();

    private Object approvalLockFor(Long userId) {
        return userApprovalLocks.computeIfAbsent(userId, id -> new Object());
    }

    /**
     * 사용 신청을 승인한다. config-server에 생성 작업만 등록하고 바로 돌아온다. 계정과 컨테이너는
     * config-server의 제어기가 단계별로 만들고, 작업 결과 폴러(ProvisionJobPoller)가 그 결과를
     * {@link #completeApprovalJob}/{@link #failApprovalJob}으로 신청에 반영한다. 이 메서드가 정상 반환한
     * 시점의 신청은 아직 PROCESSING이다.
     *
     * <p>baseline·noprobe·full 세 방식이 모두 이 경로를 쓴다. 방식 차이(재시도, 결과 확인, 접근 시험)는
     * config-server 제어기의 실행 방식에서만 난다. 옛 동기 승인 경로는 {@code legacy-sync} 태그에 남아 있다.
     *
     * <p>같은 사용자의 신청 두 건을 동시에 승인하면 뒤쪽 작업은 계정이 이미 있다는 이유로 실패하고
     * PENDING으로 되돌아간다. 다시 승인하면 그때는 계정을 재사용해 정상 처리된다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SaveRequestResponseDTO approveRequest(ApproveRequestDTO dto) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        final Long[] userIdRef = {null};
        final String[] usernameRef = {null};
        final String[] serverNameRef = {null};
        final UserCreationRequestDTO[] creationDtoRef = {null};
        final SaveRequestResponseDTO[] responseRef = {null};
        tx.execute(status -> {
            Request req = requestRepository.findByIdForUpdate(dto.requestId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            if (req.getStatus() != Status.PENDING) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
            }
            req.markAsProcessing(); // 다른 관리자의 중복 승인 시도 차단
            // 관리자가 고른 이미지/자원그룹/코멘트는 작업이 끝난 뒤 다른 스레드가 확정하므로 지금 남겨 둔다.
            ContainerImage image = containerImageRepository.findById(dto.imageId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            ResourceGroup resourceGroup = resourceGroupRepository.findById(dto.resourceGroupId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            req.prepareAsyncApproval(image, resourceGroup, dto.adminComment());

            List<UserCreationRequestDTO.SupplementaryGroup> supplementaryGroups = req.getRequestGroups().stream()
                    .map(rg -> new UserCreationRequestDTO.SupplementaryGroup(rg.getGroup().getGroupName(), rg.getGroup().getUbuntuGid()))
                    .toList();
            creationDtoRef[0] = new UserCreationRequestDTO(
                    dto.requestId(),
                    req.getUbuntuUsername(),
                    req.getUser().getUbuntuPasswordHash(),
                    req.getUser().getName(),
                    req.getUbuntuUsername(),
                    false,
                    supplementaryGroups
            );
            userIdRef[0] = req.getUser().getUserId();
            usernameRef[0] = req.getUbuntuUsername();
            serverNameRef[0] = req.getResourceGroup().getServerName();
            // 응답은 트랜잭션 안에서 만든다. 밖에서 만들면 DTO가 새로 읽는 lazy 연관(예: user.userGroups)마다
            // 초기화 목록을 따로 맞춰야 하고, 빠뜨리면 승인은 진행됐는데 관리자는 500을 받는다.
            responseRef[0] = SaveRequestResponseDTO.fromEntity(req);
            return null;
        });
        Long requestId = dto.requestId();
        Long userId = userIdRef[0];
        String username = usernameRef[0];
        String serverName = serverNameRef[0];

        // 이미 리눅스 계정이 있는 사용자면 계정 정보를 빼고 등록한다 — 그래야 제어기가 계정 단계를
        // 건너뛰고 컨테이너만 만들어, 기존 홈 디렉터리를 그대로 물려받는다.
        final boolean[] reuseAccountRef = {false};
        synchronized (approvalLockFor(userId)) {
            new TransactionTemplate(transactionManager).execute(status -> {
                User owner = userRepository.findByIdForUpdate(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                reuseAccountRef[0] = owner.hasUbuntuAccount();
                return null;
            });
        }
        boolean reuseAccount = reuseAccountRef[0];

        // 계정을 새로 만드는 경우의 그룹은 작업이 계정을 만들 때 함께 넣는다. 
        // 재사용 계정의 경우, config-server의 provision 제어기가 Pod 생성 후 그룹을 추가하므로
        // 여기서는 그룹 정보를 구성만 하고 로컬 호출은 하지 않는다.
        List<UserCreationRequestDTO.SupplementaryGroup> requestGroupsToAdd = 
               creationDtoRef[0].supplementaryGroups();

        ProvisionRegisterRequestDTO body = reuseAccount
               ? ProvisionRegisterRequestDTO.podOnly(requestId, username, requestGroupsToAdd)
               : ProvisionRegisterRequestDTO.withAccount(creationDtoRef[0]);
        Long jobId;
        try {
           jobId = operationJobService.registerProvision(body);
        } catch (Exception e) {
           // 등록 자체가 실패했으면 아직 아무것도 만들어지지 않았다 — 정리할 자원 없이 되돌린다.
           log.warn("[보상 트랜잭션] 생성 작업 등록 실패 → 상태 복구 시작: {}", username, e);
           notifyApprovalFailure(String.format(
                   "[승인 실패] 생성 작업 등록 실패로 상태를 PENDING으로 되돌렸습니다: username=%s, requestId=%d, error=%s",
                   username, requestId, e.getMessage()), serverName);
           revertToPendingIfStillProcessing(requestId, serverName);
           throw e;
        }
        // 결과 폴러가 이 번호의 결과만 반영하게 남긴다. 그 사이 상태가 바뀌었으면(거절 등) 건드리지 않는다.
        new TransactionTemplate(transactionManager).execute(status -> {
            requestRepository.findByIdForUpdate(requestId)
                    .filter(r -> r.getStatus() == Status.PROCESSING)
                    .ifPresent(r -> r.recordProvisionJob(jobId));
            return null;
        });

        return responseRef[0];
    }

    /**
     * 등록해 둔 생성 작업이 성공했을 때 신청에 반영하고 안내 메일을 보낸다. 작업 결과 폴러가 호출한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void completeApprovalJob(Long requestId, JobResultResponseDTO.Result made) {
        if (made == null || made.podName() == null) {
            // 작업은 성공했는데 만든 자원을 받지 못했다(결과 보관 기간이 지난 경우 등). 그대로 확정하면
            // 컨테이너 이름도 포트도 없는 신청이 승인 완료로 남으므로, 사람이 확인하도록 알리고 멈춘다.
            log.error("생성 작업 성공 결과에 자원 정보가 없어 신청에 반영하지 못함: requestId={}", requestId);
            notifyApprovalFailure(String.format(
                    "[승인 확인 필요] 생성 작업은 성공했으나 결과 정보를 받지 못해 신청에 반영하지 못했습니다: requestId=%d",
                    requestId), serverNameOf(requestId));
            return;
        }

        final Request[] savedRequestRef = {null};
        new TransactionTemplate(transactionManager).execute(status -> {
            Request req = requestRepository.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            if (req.getStatus() != Status.PROCESSING) {
                // 작업이 도는 동안 다른 관리자가 거절했을 수 있다. 덮어쓰지 않는다.
                log.warn("생성 작업 성공을 반영하려 했으나 상태가 변경됨 - requestId={}, 현재 상태={}",
                        requestId, req.getStatus());
                return null;
            }
            User owner = userRepository.findByIdForUpdate(req.getUser().getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            if (made.uid() != null && made.gid() != null) {
                // 작업이 계정을 새로 만든 경우다. 재사용한 경우엔 결과에 uid가 없고 User에 이미 있다.
                owner.assignUbuntuAccount(made.uid(), made.gid());
            }
            req.assignUbuntuIds(owner.getUbuntuUid(), owner.getUbuntuGid());
            req.assignPodInfo(made.podName(), made.node());
            req.completeApproval();
            // 작업이 성공했다는 건 이 신청이 요청한 그룹이 실제로 AD(계정 단위)에 반영됐다는 뜻이다.
            // request_groups는 "신청 시점에 고른 그룹"만 담당하고, 실제 계정 상태는 User.userGroups로
            // 옮겨 담는다 — 같은 계정의 다른 컨테이너에서도 이 그룹을 가진 것으로 보이게 하기 위함.
            for (RequestGroup rg : req.getRequestGroups()) {
                owner.addGroupIfAbsent(rg.getGroup());
            }
            if (made.ports() != null) {
                for (CreatePodResponseDTO.PortInfo port : made.ports()) {
                    podExternalPortRepository.save(PodExternalPort.builder()
                            .request(req)
                            .internalPort(port.internalPort())
                            .externalPort(port.externalPort())
                            .usagePurpose(port.usagePurpose())
                            .build());
                }
            }
            // 트랜잭션 종료 후 사용되는 모든 lazy 연관 초기화
            req.getUser().getEmail();
            req.getContainerImage().getImageName();
            req.getResourceGroup().getServerName();
            req.getRequestGroups().size();
            savedRequestRef[0] = req;
            return null;
        });

        Request savedRequest = savedRequestRef[0];
        if (savedRequest == null) {
            return;
        }
        String sshPort = externalPortOf(made, "ssh");
        String jupyterPort = externalPortOf(made, "jupyter");
        sendNotificationSafely(
                () -> alarmService.sendContainerCreatedEmail(savedRequest, sshPort, jupyterPort),
                () -> log.info("사용자 '{}'에게 컨테이너 배정 안내 메일을 발송했습니다.", savedRequest.getUser().getName()),
                e -> log.warn("사용자 '{}'에게 배정 안내 메일 발송 실패. (RequestId: {})",
                        savedRequest.getUser().getName(), savedRequest.getRequestId(), e)
        );
    }

    /**
     * 등록해 둔 생성 작업이 실패했을 때 정리한다. 이번 작업이 만든 계정을 되돌리는 것은 config-server
     * 제어기가 이미 수행했으므로(노드를 모르거나 그 계정의 다른 컨테이너가 남아 있으면 보류), 여기서는
     * 신청 상태만 되돌리고 알린다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void failApprovalJob(Long requestId, JobResultResponseDTO result) {
        String serverName = serverNameOf(requestId);
        notifyApprovalFailure(String.format(
                "[승인 실패] 생성 작업이 실패해 상태를 PENDING으로 되돌렸습니다: requestId=%d, error=%s",
                requestId, result.errorCode()), serverName);
        revertToPendingIfStillProcessing(requestId, serverName);
    }

    /**
     * 결과 불명(UNKNOWN)인 작업을 알린다. 실행 여부 자체를 알 수 없는 상태라 되돌리지 않는다 —
     * 되돌리면 실제로는 만들어진 자원이 남은 채 신청만 PENDING이 되어, 재승인 때 중복 생성으로
     * 이어질 수 있다. 신청은 PROCESSING에 둔 채 관리자 점검 대상으로 남긴다.
     */
    public void reportUnknownApprovalJob(Long requestId, JobResultResponseDTO result) {
        notifyApprovalFailure(String.format(
                "[승인 확인 필요] 생성 작업의 결과가 불명입니다. 자원이 남아 있는지 확인이 필요합니다: requestId=%d, error=%s",
                requestId, result.errorCode()), serverNameOf(requestId));
    }

    /**
     * 제어기가 재시도로 해소하지 못하고 자원을 남긴 채 넘긴 작업(DEGRADED)을 알린다. 컨테이너·계정이 남아
     * 있으므로 되돌리지 않는다 — 되돌리면 재승인 때 컨테이너가 하나 더 만들어진다. 신청은 PROCESSING에 둔 채
     * 관리자가 원인을 확인하고 정리하도록 넘긴다.
     */
    public void reportDegradedApprovalJob(Long requestId, JobResultResponseDTO result) {
        notifyApprovalFailure(String.format(
                "[승인 확인 필요] 생성 작업이 복구되지 않아 관리자 확인으로 넘어왔습니다. 만든 자원은 남아 있습니다: requestId=%d, jobId=%s",
                requestId, result.jobId()), serverNameOf(requestId));
    }

    /** 알림을 관리자가 실제로 보는 farm/lab 채널로 보내기 위한 서버 구분. 조회 실패는 알림 실패로 번지지 않게 삼킨다. */
    private String serverNameOf(Long requestId) {
        try {
            return new TransactionTemplate(transactionManager).execute(status ->
                    requestRepository.findById(requestId)
                            .map(req -> req.getResourceGroup().getServerName())
                            .orElse(null));
        } catch (Exception e) {
            log.warn("서버 구분 조회 실패: requestId={}", requestId, e);
            return null;
        }
    }

    /** 작업 결과의 포트 목록에서 usage_purpose가 일치하는 첫 외부 포트(문자열). 없으면 "". */
    private String externalPortOf(JobResultResponseDTO.Result made, String purpose) {
        if (made.ports() == null) {
            return "";
        }
        return made.ports().stream()
                .filter(p -> purpose.equalsIgnoreCase(p.usagePurpose()))
                .map(p -> String.valueOf(p.externalPort()))
                .findFirst().orElse("");
    }

    @Transactional
    public SaveRequestResponseDTO rejectRequest(RejectRequestDTO dto) {
        // 행 잠금: PROCESSING 상태를 거절하는 동안 completeApprovalJob의 승인 확정
        // 트랜잭션과 순서가 뒤섞이지 않게 한다. completeApprovalJob도 확정 직전에 상태를 다시 확인한다.
        Request request = requestRepository.findByIdForUpdate(dto.requestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!(request.getStatus() == Status.PENDING || request.getStatus() == Status.PROCESSING || request.getStatus() == Status.FULFILLED)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }
        request.reject(dto.adminComment());
        sendNotificationSafely(
                () -> alarmService.sendRequestRejectedEmail(request, dto.adminComment()),
                () -> {},
                e -> log.warn("거절 안내 메일 발송 실패: requestId={}", dto.requestId(), e)
        );
        return SaveRequestResponseDTO.fromEntity(request);
    }


    @Transactional
    public void rejectModification(Long adminId, RejectModificationDTO dto) {
        // approveModification과 동일하게 행 잠금으로 조회한다 — 같은 행에 대한 같은 PENDING
        // 검증인데 한쪽만 잠그면, 승인과 거절이 동시에 들어왔을 때 둘 다 검증을 통과한다.
        ChangeRequest changeRequest = changeRequestRepository.findByIdForUpdate(dto.changeRequestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (changeRequest.getStatus() != Status.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        changeRequest.deny(admin, dto.adminComment());
        sendNotificationSafely(
                () -> alarmService.sendModificationRejectedEmail(changeRequest, dto.adminComment()),
                () -> {},
                e -> log.warn("변경 요청 거절 메일 발송 실패: changeRequestId={}", dto.changeRequestId(), e)
        );
    }

    /**
     * GROUP 타입은 config-server 외부 호출(AD 반영)이 끼기 때문에 메서드 전체를 하나의 물리 트랜잭션으로
     * 묶지 않는다 — 외부 호출 성공 후 트랜잭션이 롤백되면 DB는 되돌아가도 AD/원장은 반영된 채 남아,
     * NAS가 AD를 보고 판정하는 접근 권한만 DB 기록 없이 새는 상태가 되기 때문이다(admin_be#554).
     * 그래서 approveRequest(:99)와 같은 3단계 패턴을 쓴다: ①잠금+검증+그룹 해석(트랜잭션) →
     * ②외부 호출(트랜잭션 밖) → ③재검증+커밋(새 트랜잭션). 나머지 4개 ChangeType은 외부 호출이 없어
     * 단일 트랜잭션 그대로 처리한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void approveModification(Long adminId, ApproveModificationDTO dto) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        AtomicReference<Long> originalRequestIdRef = new AtomicReference<>();
        AtomicReference<String> usernameRef = new AtomicReference<>();
        AtomicReference<String> serverNameRef = new AtomicReference<>();
        AtomicReference<List<String>> newGroupNamesRef = new AtomicReference<>();
        AtomicReference<Set<Long>> newGroupIdsRef = new AtomicReference<>();
        AtomicBoolean deferredRef = new AtomicBoolean(false);
        AtomicReference<ExpiryChangeResult> expiryChangeRef = new AtomicReference<>();
        AtomicReference<ChangeRequest> committedChangeRequestRef = new AtomicReference<>();
        AtomicReference<Request> committedOriginalRequestRef = new AtomicReference<>();

        tx.executeWithoutResult(status -> {
            // 행 잠금 조회: 동시에 같은 변경 요청을 승인 시도하는 두 번째 트랜잭션은 첫 트랜잭션 커밋까지 대기하다가
            // FULFILLED 상태를 보고 실패한다 (PORT 등 부수 효과의 중복 실행 방지)
            ChangeRequest changeRequest = changeRequestRepository.findByIdForUpdate(dto.changeRequestId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            if (changeRequest.getStatus() != Status.PENDING) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
            }
            User admin = userRepository.findById(adminId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

            Request lazyOriginalRequest = changeRequest.getRequest();
            if (lazyOriginalRequest == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            // 실제로 필드를 덮어쓰는 대상은 ChangeRequest가 아니라 이 Request다. 잠금이 걸린 건
            // ChangeRequest 행뿐이므로, 여기서 Request 행도 직접 잠가야 한다 — 그러지 않으면
            // 마이그레이션/만료 정리가 이 행을 동시에 다루는 중에도 검증을 통과한다.
            Request originalRequest = requestRepository.findByIdForUpdate(lazyOriginalRequest.getRequestId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            // ChangeRequest는 FULFILLED 상태를 전제로 신청된다. 그 사이 원본 Request가 삭제되거나
            // 마이그레이션/재승인 처리 중으로 넘어갔는데 상태 확인 없이 그대로 적용하면, 이미 죽었거나
            // 다른 트랜잭션이 다루고 있는 Request의 필드를 조용히 덮어써 정합성이 깨진다.
            // 잠금을 잡은 뒤에 다시 확인해야 잠금 대기 중 커밋된 최신 상태를 본다.
            if (originalRequest.getStatus() != Status.FULFILLED) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
            }

            if (changeRequest.getChangeType() == ChangeType.GROUP) {
                Set<Long> newGroupIds;
                try {
                    newGroupIds = objectMapper.readValue(changeRequest.getNewValue(),
                            objectMapper.getTypeFactory().constructCollectionType(Set.class, Long.class));
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse change request value: {}", e.getMessage());
                    throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
                }
                Set<Group> newGroups = resolveGroups(newGroupIds);

                originalRequestIdRef.set(originalRequest.getRequestId());
                usernameRef.set(originalRequest.getUbuntuUsername());
                serverNameRef.set(originalRequest.getResourceGroup().getServerName());
                newGroupNamesRef.set(newGroups.stream().map(Group::getGroupName).toList());
                newGroupIdsRef.set(newGroupIds);
                deferredRef.set(true);
                return;
            }

            ChangeApplier applier = changeAppliers().get(changeRequest.getChangeType());
            if (applier == null) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_CHANGE_TYPE);
            }

            ExpiryChangeResult expiryChange;
            try {
                expiryChange = applier.apply(originalRequest, changeRequest.getNewValue());
            } catch (JsonProcessingException e) {
                log.error("Failed to parse change request value: {}", e.getMessage());
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            changeRequest.approve(admin, dto.adminComment());

            // 트랜잭션 종료 후(알림 발송 시점) 사용되는 지연 로딩 필드를 미리 초기화
            originalRequest.getUser().getEmail();
            expiryChangeRef.set(expiryChange);
            committedChangeRequestRef.set(changeRequest);
            committedOriginalRequestRef.set(originalRequest);
        });

        if (deferredRef.get()) {
            // 트랜잭션 밖 — 여기서 실패하면 위 트랜잭션이 이미 커밋 없이 끝난 뒤라 DB엔 아무 변경도
            // 없다. 신청은 그대로 PENDING에 남고, 예외가 그대로 호출자에게 전파된다.
            groupService.addUserToGroups(usernameRef.get(), newGroupNamesRef.get());
            // AD 반영이 끝난 시점에 바로 트리거한다 — 아래 DB 커밋 성공 여부와 무관하게 AD는
            // 이미 바뀌었으므로 NAS 쪽 반영도 그만큼 빨리 시작하는 게 맞다(admin_infra-proposed#161).
            groupService.triggerNasGssFlush(usernameRef.get());

            tx.executeWithoutResult(status -> {
                ChangeRequest changeRequest = changeRequestRepository.findByIdForUpdate(dto.changeRequestId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
                Request originalRequest = requestRepository.findByIdForUpdate(originalRequestIdRef.get())
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

                if (changeRequest.getStatus() != Status.PENDING || originalRequest.getStatus() != Status.FULFILLED) {
                    // AD 반영은 이미 끝났다. 자동 보상 삭제는 하지 않는다 — newGroups에는 신청 전부터
                    // 소속돼 있던 그룹도 섞일 수 있어 일괄 제거하면 기존 소속까지 끊긴다. 방치하면
                    // 아무도 모르는 채로 남으므로 반드시 알리고, 필요하면 관리자가 그룹 제거 API
                    // (DELETE /api/admin/users/{id}/groups/{groupId})로 개별 정리한다.
                    log.error("[approveModification] AD 그룹 반영 완료 후 상태 불일치로 DB 커밋 실패 - 수동 확인 필요: " +
                                    "changeRequestId={}, requestId={}, groups={}",
                            dto.changeRequestId(), originalRequestIdRef.get(), newGroupNamesRef.get());
                    notifyApprovalFailure(String.format(
                            "[approveModification] AD 그룹 반영은 완료됐으나 상태 변경으로 DB에 기록하지 못했습니다 - " +
                                    "수동 확인 필요: changeRequestId=%d, requestId=%d, username=%s, groups=%s",
                            dto.changeRequestId(), originalRequestIdRef.get(), usernameRef.get(), newGroupNamesRef.get()
                    ), serverNameRef.get());
                    throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
                }

                User admin = userRepository.findById(adminId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                // 계정(User) 단위로 누적한다 — config-server의 add_user_groups가 추가 전용이라
                // AD에선 절대 안 빠지는데 여기서 clear()로 지우면 DB가 AD보다 뒤처진 거짓 상태가 된다.
                // originalRequest가 아니라 그 소유자(User)에 반영해야 같은 계정의 다른 컨테이너에도
                // 이 그룹이 반영된 것으로 보인다.
                User owner = userRepository.findByIdForUpdate(originalRequest.getUser().getUserId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                Set<Group> newGroups = resolveGroups(newGroupIdsRef.get());
                for (Group g : newGroups) {
                    owner.addGroupIfAbsent(g);
                }
                changeRequest.approve(admin, dto.adminComment());

                originalRequest.getUser().getEmail();
                committedChangeRequestRef.set(changeRequest);
                committedOriginalRequestRef.set(originalRequest);
            });
        }

        ChangeRequest committedChangeRequest = committedChangeRequestRef.get();
        Request committedOriginalRequest = committedOriginalRequestRef.get();
        ExpiryChangeResult expiryChange = expiryChangeRef.get();

        if (expiryChange != null) {
            sendNotificationSafely(
                    () -> alarmService.sendContainerExtendedEmail(committedOriginalRequest, expiryChange.oldExpiresAt(), expiryChange.newExpiresAt()),
                    () -> log.info("사용자 '{}'에게 기간 연장 안내 메일을 발송했습니다.", committedOriginalRequest.getUser().getName()),
                    e -> log.warn("기간 연장 안내 메일 발송 실패: changeRequestId={}", dto.changeRequestId(), e)
            );
        } else if (deferredRef.get()) {
            List<String> groupNames = newGroupNamesRef.get();
            sendNotificationSafely(
                    () -> alarmService.sendGroupAddedEmail(committedChangeRequest, dto.adminComment(), groupNames),
                    () -> log.info("사용자 '{}'에게 그룹 추가 승인 안내 메일을 발송했습니다.", committedOriginalRequest.getUser().getName()),
                    e -> log.warn("그룹 추가 승인 안내 메일 발송 실패: changeRequestId={}", dto.changeRequestId(), e)
            );
        } else {
            sendNotificationSafely(
                    () -> alarmService.sendModificationApprovedEmail(committedChangeRequest, dto.adminComment()),
                    () -> log.info("사용자 '{}'에게 변경 요청 승인 안내 메일을 발송했습니다.", committedOriginalRequest.getUser().getName()),
                    e -> log.warn("변경 요청 승인 안내 메일 발송 실패: changeRequestId={}", dto.changeRequestId(), e)
            );
        }
    }

    // ── approveModification: ChangeType별 적용 로직 ──────────────────────
    // Map으로 등록해두면 새 ChangeType이 추가될 때 이 메서드 자체를 수정하지 않고
    // applier 하나만 더 등록하면 된다 (개방-폐쇄 원칙).

    // GROUP은 config-server 외부 호출이 끼어 있어 이 맵을 거치지 않고 approveModification에서
    // 직접 분기한다(3단계 트랜잭션 분리, admin_be#554) — 여기 등록하면 죽은 코드가 된다.
    private Map<ChangeType, ChangeApplier> changeAppliers() {
        return Map.of(
                ChangeType.EXPIRES_AT, this::applyExpiresAtChange,
                ChangeType.RESOURCE_GROUP, this::applyResourceGroupChange,
                ChangeType.CONTAINER_IMAGE, this::applyContainerImageChange,
                ChangeType.PORT, this::applyPortChange
        );
    }

    private ExpiryChangeResult applyExpiresAtChange(Request originalRequest, String newValueJson) throws JsonProcessingException {
        LocalDateTime newExpiresAt = LocalDateTime.parse(objectMapper.readValue(newValueJson, String.class));
        LocalDateTime oldExpiresAt = originalRequest.getExpiresAt();
        originalRequest.updateExpiresAt(newExpiresAt);
        return new ExpiryChangeResult(oldExpiresAt, newExpiresAt);
    }

    /** GID 목록을 Group 엔티티로 해석한다. approveModification의 1단계(사전 검증)와 3단계(재적용) 양쪽에서 쓴다. */
    private Set<Group> resolveGroups(Set<Long> gids) {
        return gids.stream()
                .map(gid -> groupRepository.findByUbuntuGid(gid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)))
                .collect(Collectors.toSet());
    }

    private ExpiryChangeResult applyResourceGroupChange(Request originalRequest, String newValueJson) throws JsonProcessingException {
        Integer newResourceGroupId = objectMapper.readValue(newValueJson, Integer.class);
        ResourceGroup newResourceGroup = resourceGroupRepository.findById(newResourceGroupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        originalRequest.updateResourceGroup(newResourceGroup);
        return null;
    }

    private ExpiryChangeResult applyContainerImageChange(Request originalRequest, String newValueJson) throws JsonProcessingException {
        Long newImageId = objectMapper.readValue(newValueJson, Long.class);
        ContainerImage newContainerImage = containerImageRepository.findById(newImageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        originalRequest.updateContainerImage(newContainerImage);
        return null;
    }

    private ExpiryChangeResult applyPortChange(Request originalRequest, String newValueJson) throws JsonProcessingException {
        List<PortRequestDTO> newPorts = objectMapper.readValue(newValueJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, PortRequestDTO.class));
        for (PortRequestDTO portRequestDTO : newPorts) {
            portRequestService.createPortRequest(
                    originalRequest,
                    originalRequest.getResourceGroup(),
                    portRequestDTO.internalPort(),
                    portRequestDTO.usagePurpose()
            );
        }
        return null;
    }

    @FunctionalInterface
    private interface ChangeApplier {
        /** newValueJson을 파싱해 originalRequest에 반영한다. EXPIRES_AT 변경일 때만 이전/이후 만료일을 담아 반환한다. */
        ExpiryChangeResult apply(Request originalRequest, String newValueJson) throws JsonProcessingException;
    }

    private record ExpiryChangeResult(LocalDateTime oldExpiresAt, LocalDateTime newExpiresAt) {}

    /** 알림 발송을 시도하고, 실패해도 예외를 전파하지 않는다 (알림은 부가 기능 — 실패해도 이미 반영된 상태 변경을 되돌리지 않는다). */
    private void sendNotificationSafely(Runnable emailSend, Runnable onSuccess, Consumer<Exception> onFailure) {
        try {
            emailSend.run();
            onSuccess.run();
        } catch (Exception e) {
            onFailure.accept(e);
        }
    }

    // ── 보상 트랜잭션 헬퍼 ─────────────────────────────────────────────

    // 보상 트랜잭션 자체의 실패는 로그만 남기면 관리자가 직접 읽기 전까지 아무도 모른다 —
    // 원래 실패(계정/Pod 생성 실패 등) 위에 이 정리마저 실패했다는 건 인프라와 DB가 어긋난
    // 채로 방치된다는 뜻이라 즉시 알림이 필요하다. 관리자가 "새로운 서버 사용 신청" 알림을
    // 실제로 보는 farm/lab 채널로 보내야 놓치지 않는다 — 범용 에러 채널은 잘 안 보게 된다.
    private void alertCompensationFailure(String message, String serverName) {
        try {
            alarmService.sendAdminSlackNotification(serverName, message);
        } catch (Exception ignored) {
            // 알림 발송 실패가 원래 예외 전파를 막으면 안 된다.
        }
    }

    // 승인 실패는 대부분 작업 결과 폴러가 뒤늦게 알게 되어 승인을 누른 관리자의 HTTP 응답으로는 보이지 않는다.
    // 관리자가 신청 목록만 보고 원인을 모른 채 재승인하지 않도록 farm/lab 채널에 실패 이력을 남긴다.
    // (보상 자체의 실패가 아니라 원래 승인 처리의 실패를 알린다는 점만 alertCompensationFailure와 다르다.)
    private void notifyApprovalFailure(String message, String serverName) {
        try {
            alarmService.sendAdminSlackNotification(serverName, message);
        } catch (Exception ignored) {
        }
    }

    // 승인이 실패한 신청을 PENDING으로 되돌린다. 실패는 두 경우로 갈린다. ① 그 사이 다른 관리자가
    // 거절해 상태가 이미 DENIED 등으로 바뀐 경우(건드리지 않는다) ② 여전히 PROCESSING인 채 실패한 경우.
    // 상태 확인 없이 덮어쓰면 ①에서 다른 관리자의 거절 결정을 지우고, ②를 그대로 두면 신청이 재승인도
    // 거절도 못 하는 PROCESSING에 갇힌다. 그래서 락 + 상태 재확인을 거친다. 작업 등록 실패, 작업 실패
    // (작업 결과 폴러), 작업 없이 오래 멈춘 신청(RequestSchedulerService 재조정)에서 쓴다.
    public void revertToPendingIfStillProcessing(Long requestId, String serverName) {
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                requestRepository.findByIdForUpdate(requestId)
                        .filter(req -> req.getStatus() == Status.PROCESSING)
                        .ifPresent(Request::revertToPending);
                return null;
            });
        } catch (Exception e) {
            log.error("[보상 트랜잭션 실패] 요청 상태 복구 실패 — 수동 확인 필요: requestId={}", requestId, e);
            alertCompensationFailure(String.format("[보상 트랜잭션 실패] 요청 상태 복구 실패 - 수동 확인 필요: requestId=%d", requestId), serverName);
        }
    }
}
