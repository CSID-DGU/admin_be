package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ProvisionRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.UserCreationRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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
    private final PodExternalPortRepository podExternalPortRepository;
    private final OperationJobService operationJobService;
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

    // 성공 결과를 받지 못해 반영을 멈춘 신청. 같은 알림이 폴러 주기마다 반복되지 않게 한 번 알린 신청을 기억한다.
    private final Set<Long> reportedMissingResult = ConcurrentHashMap.newKeySet();

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
            // 비밀번호 변경과 같은 사용자 행 잠금으로 직렬화한다 — 잠금 없이 읽으면 변경 직전의 옛 해시로
            // 계정이 만들어질 수 있다(비밀번호 변경은 PROCESSING 신청이 있으면 거절한다).
            User owner = userRepository.findByIdForUpdate(req.getUser().getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            // 계정 회수가 도는 중이면 새 컨테이너가 곧 지워질 계정을 쓰게 된다. 회수가 끝난 뒤 승인하면 되살린다.
            if (owner.isReleasingUbuntuAccount()) {
                throw new BusinessException(ErrorCode.UBUNTU_ACCOUNT_RELEASING);
            }
            creationDtoRef[0] = new UserCreationRequestDTO(
                    dto.requestId(),
                    req.getUbuntuUsername(),
                    owner.getUbuntuPasswordHash(),
                    req.getUser().getName(),
                    req.getUbuntuUsername(),
                    false,
                    supplementaryGroups,
                    previousUbuntuUid(req.getUser())
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
        Long jobId = registerProvisionJob(body, requestId, username, serverName);
        // 결과 폴러가 이 번호의 결과만 반영하게 남긴다. 그 사이 상태가 바뀌었으면(거절 등) 건드리지 않는다.
        new TransactionTemplate(transactionManager).execute(status -> {
            requestRepository.findByIdForUpdate(requestId)
                    .filter(r -> r.getStatus() == Status.PROCESSING)
                    .ifPresent(r -> r.recordJob(jobId));
            return null;
        });

        return responseRef[0];
    }

    private Long registerProvisionJob(ProvisionRegisterRequestDTO body, Long requestId, String username, String serverName) {
        try {
            return operationJobService.registerProvision(body);
        } catch (Exception e) {
            return registeredJobDespiteFailure(requestId, username, serverName, e);
        }
    }

    /**
     * 등록 요청이 실패로 보였을 때, 실제로 등록된 작업이 도는지 확인한다. 응답만 늦었거나(타임아웃) 앞선 승인이
     * 남긴 작업이 아직 도는 경우(409) 작업은 config-server에 있다 — 그때 신청을 되돌리면 그 작업이 만든 컨테이너를
     * 어떤 신청도 가리키지 않게 된다.
     *
     * @return 도는 중인 작업 번호. 이 신청은 PROCESSING으로 두고 결과 폴러가 이어받는다.
     * @throws RuntimeException 작업이 없거나 끝났으면 PENDING으로 되돌린 뒤, 작업 상태를 모르면 되돌리지 않고
     *                          (재조정 스케줄러가 작업 상태를 보고 판단한다) 원래 오류를 던진다
     */
    private Long registeredJobDespiteFailure(Long requestId, String username, String serverName, Exception cause) {
        JobResultResponseDTO job;
        try {
            job = operationJobService.getResult(OperationJobService.KIND_PROVISION, requestId);
        } catch (Exception lookupFailure) {
            log.warn("생성 작업 등록 실패 후 작업 상태 조회도 실패 — PROCESSING 유지, 재조정에 맡김: requestId={}", requestId, lookupFailure);
            notifyApprovalFailure(String.format(
                    "[승인 확인 필요] 생성 작업 등록 결과를 확인하지 못해 PROCESSING으로 두었습니다(작업이 없으면 재조정이 되돌립니다): username=%s, requestId=%d, error=%s",
                    username, requestId, cause.getMessage()), serverName);
            throw asRuntime(cause);
        }
        if (job != null && OperationJobService.PHASE_START.equals(job.phase())) {
            log.warn("생성 작업 등록 응답은 실패했지만 작업이 도는 중 — 이어받음: requestId={}, jobId={}", requestId, job.jobId(), cause);
            return job.jobId();
        }
        // 등록된 작업이 없다 — 아직 아무것도 만들어지지 않았으므로 정리할 자원 없이 되돌린다.
        log.warn("[보상 트랜잭션] 생성 작업 등록 실패 → 상태 복구 시작: {}", username, cause);
        notifyApprovalFailure(String.format(
                "[승인 실패] 생성 작업 등록 실패로 상태를 PENDING으로 되돌렸습니다: username=%s, requestId=%d, error=%s",
                username, requestId, cause.getMessage()), serverName);
        revertToPendingIfStillProcessing(requestId, serverName);
        throw asRuntime(cause);
    }

    private static RuntimeException asRuntime(Exception e) {
        return e instanceof RuntimeException re ? re : new BusinessException(ErrorCode.POD_CREATION_FAILED);
    }

    /**
     * 계정을 새로 만들 때 config-server에 보낼 expected_uid. 계정이 살아 있으면 계정 생성 자체를 건너뛰므로 필요 없다.
     * 이 사람이 예전에 받은 UID가 있으면(회수 후 재승인) 그 번호를 보낸다 — 원장에 계정이 남았으면 이 값과
     * 원장 UID가 같을 때만 이어받고, 없으면 NAS 홈 소유자가 이 값일 때 같은 UID로 다시 만든다.
     * 첫 승인이면 null이라 새 번호를 받는다.
     */
    private Long previousUbuntuUid(User user) {
        return user.hasUbuntuAccount() ? null : user.getUbuntuUid();
    }

    /**
     * 등록해 둔 생성 작업이 성공했을 때 신청에 반영하고 안내 메일을 보낸다. 작업 결과 폴러가 호출한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void completeApprovalJob(Long requestId, JobResultResponseDTO.Result made) {
        if (made == null || made.podName() == null) {
            // 작업은 성공했는데 만든 자원을 받지 못했다(결과 보관 기간이 지난 경우 등). 그대로 확정하면
            // 컨테이너 이름도 포트도 없는 신청이 승인 완료로 남으므로, 사람이 확인하도록 알리고 멈춘다.
            // 신청이 PROCESSING에 남아 폴러가 매 바퀴 다시 부르므로 알림은 신청마다 한 번만 보낸다.
            if (!reportedMissingResult.add(requestId)) {
                return;
            }
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
            if (!owner.hasUbuntuAccount() && made.uid() != null && made.gid() != null) {
                // 작업이 계정을 새로 만들었거나 원장에 남은 계정을 이어받은 경우다. 이미 기록이 있으면
                // 재사용 경로라 결과의 UID는 같은 값이다.
                owner.assignUbuntuAccount(made.uid(), made.gid());
            }
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

    /**
     * 신청을 거절한다. 거절은 상태만 DENIED로 바꾸고 인프라 자원을 회수하지 않으므로, 자원이 생길 수 있는
     * 상태에서는 막는다. 사용 중인(FULFILLED) 컨테이너는 컨테이너 회수로 끝낸다.
     *
     * <p>PROCESSING은 생성 작업이 끝난 뒤(실패·결과 불명·관리자 이관·작업 없음)에만 거절한다. 도는 중이거나
     * 성공을 아직 반영하지 않았으면 거절하는 순간 그 작업이 만든 컨테이너를 어떤 신청도 가리키지 않게 된다.
     * 성공했지만 결과가 사라져 반영하지 못한 신청은 갇혀 있으므로 거절을 허용한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SaveRequestResponseDTO rejectRequest(RejectRequestDTO dto) {
        Long requestId = dto.requestId();
        Status current = new TransactionTemplate(transactionManager).execute(status ->
                requestRepository.findById(requestId)
                        .map(Request::getStatus)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)));
        if (current == Status.PROCESSING) {
            ensureProvisionJobFinished(requestId);
        }

        final Request[] rejectedRef = {null};
        final SaveRequestResponseDTO[] responseRef = {null};
        new TransactionTemplate(transactionManager).execute(status -> {
            // 행 잠금 + 상태 재확인: 위 조회 뒤에 폴러가 승인을 확정했다면 FULFILLED가 되어 여기서 막힌다.
            Request request = requestRepository.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            if (request.getStatus() != Status.PENDING && request.getStatus() != Status.PROCESSING) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
            }
            request.reject(dto.adminComment());
            // 응답은 트랜잭션 안에서 만든다(승인과 같은 이유). 메일이 쓰는 사용자·서버 정보도 이때 읽힌다.
            responseRef[0] = SaveRequestResponseDTO.fromEntity(request);
            rejectedRef[0] = request;
            return null;
        });
        Request rejected = rejectedRef[0];
        sendNotificationSafely(
                () -> alarmService.sendRequestRejectedEmail(rejected, dto.adminComment()),
                () -> {},
                e -> log.warn("거절 안내 메일 발송 실패: requestId={}", requestId, e)
        );
        return responseRef[0];
    }

    /** 생성 작업이 아직 돌거나 성공 결과를 반영하기 전이면 거절을 막는다. 작업 상태를 모르면 막는다. */
    private void ensureProvisionJobFinished(Long requestId) {
        Request request = new TransactionTemplate(transactionManager).execute(status ->
                requestRepository.findById(requestId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)));
        if (OperationJobService.awaitingRegistration(request.getJobId(), request.getUpdatedAt())) {
            throw new BusinessException(ErrorCode.PROVISION_JOB_IN_PROGRESS);
        }
        JobResultResponseDTO job = operationJobService.getResult(OperationJobService.KIND_PROVISION, requestId);
        if (OperationJobService.isFromOtherJob(request.getJobId(), job)) {
            // 이번 승인의 작업이 아직 보이지 않는다(이전 작업 결과가 보임).
            throw new BusinessException(ErrorCode.PROVISION_JOB_IN_PROGRESS);
        }
        boolean running = OperationJobService.PHASE_START.equals(job.phase());
        boolean successPending = OperationJobService.PHASE_SUCCESS.equals(job.phase())
                && job.result() != null && job.result().podName() != null;
        if (running || successPending) {
            throw new BusinessException(ErrorCode.PROVISION_JOB_IN_PROGRESS);
        }
    }

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
