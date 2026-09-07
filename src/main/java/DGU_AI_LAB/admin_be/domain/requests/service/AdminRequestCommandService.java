package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.service.PortRequestService;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.PortRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.UserCreationRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final PodExternalPortRepository podExternalPortRepository;
    private final PodService podService;
    private final UbuntuAccountService ubuntuAccountService;
    private final PortRequestService portRequestService;
    private final ObjectMapper objectMapper;

    private final @Qualifier("configWebClient") WebClient userCreationWebClient;
    private final PlatformTransactionManager transactionManager;

    // 계정 생성·Pod 생성을 포함한 승인 후처리 전체를 이 executor로 비동기 실행한다.
    // corePoolSize=maxPoolSize=3, queueCapacity=0(AsyncConfig 참고) — 이 이상 동시에 승인이
    // 몰리면 큐잉하지 않고 즉시 TaskRejectedException으로 거부해, 관리자에게 명확한 에러로
    // 실패시킨다 (기존 Semaphore(3) fail-fast 정책과 동일한 사용자 체감 동작 유지).
    private final ThreadPoolTaskExecutor approvalExecutor;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SaveRequestResponseDTO approveRequest(ApproveRequestDTO dto) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 1. 상태 검증 + HTTP 요청 데이터 추출 + 즉시 응답 DTO 준비 (짧은 트랜잭션, 이후 커넥션 반납)
        // 계정/Pod 생성(최대 10분)은 아래에서 비동기로 넘기므로, 관리자 HTTP 요청은 이 짧은
        // 트랜잭션만 기다리면 된다. 실제 처리 완료 여부는 기존 Pod 생성 진행 상태 폴링
        // API(/pod-status/pods/{username}/status)로 확인한다 — 승인/일반 Pod 생성 모두
        // config-server가 같은 stage 체계를 쓰므로 별도 API 없이 그대로 재사용된다.
        final UserCreationRequestDTO[] creationDtoRef = {null};
        final String[] usernameRef = {null};
        // 보상 트랜잭션 실패 알림을 관리자가 실제로 보는 farm/lab 채널로 보내기 위해
        // 승인 시점의 서버 구분을 미리 떼어둔다 (resourceGroup은 신청 시점부터 항상 존재).
        final String[] serverNameRef = {null};
        final SaveRequestResponseDTO[] responseRef = {null};
        tx.execute(status -> {
            // 행 잠금 조회: 동시에 같은 요청을 승인 시도하는 두 번째 트랜잭션은 여기서 대기하다가
            // 첫 트랜잭션 커밋 후 PROCESSING 상태를 보고 아래에서 실패한다 (중복 승인/중복 provisioning 방지)
            Request req = requestRepository.findByIdForUpdate(dto.requestId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            if (req.getStatus() != Status.PENDING) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
            }
            req.markAsProcessing(); // 다른 관리자의 중복 승인 시도 차단
            usernameRef[0] = req.getUbuntuUsername();
            serverNameRef[0] = req.getResourceGroup().getServerName();
            List<UserCreationRequestDTO.SupplementaryGroup> supplementaryGroups = req.getRequestGroups().stream()
                    .map(rg -> new UserCreationRequestDTO.SupplementaryGroup(rg.getGroup().getGroupName(), rg.getGroup().getUbuntuGid()))
                    .toList();
            creationDtoRef[0] = new UserCreationRequestDTO(
                    req.getUbuntuUsername(),
                    req.getUbuntuPasswordBase64(),
                    req.getUser().getName(),
                    req.getUbuntuUsername(),
                    false,
                    supplementaryGroups
            );
            // 트랜잭션 종료 후 사용되는 모든 lazy 연관 초기화 (즉시 응답 DTO 빌드용)
            req.getUser().getEmail();
            req.getContainerImage().getImageName();
            req.getResourceGroup().getServerName();
            req.getRequestGroups().size();
            responseRef[0] = SaveRequestResponseDTO.fromEntity(req);
            return null;
        });
        String username = usernameRef[0];
        Long requestId = dto.requestId();
        String serverName = serverNameRef[0];
        UserCreationRequestDTO creationDto = creationDtoRef[0];

        // 2~4단계(계정 생성, Pod 생성, DB 반영, 메일)를 비동기로 넘긴다. executor 큐가
        // 꽉 차서(동시 3건 초과) 거부되면 제출 시점에 곧바로 TaskRejectedException이
        // 던져지므로(작업 실행 자체가 아니라 제출이 동기 호출이라 그렇다), 여기서 잡아서
        // 기존과 동일하게 즉시 실패시킨다.
        try {
            approvalExecutor.execute(() -> processApproval(requestId, username, creationDto, dto, serverName));
        } catch (TaskRejectedException e) {
            log.warn("[동시 처리 한도 초과] 승인 후처리 제출 거부 → 상태 복구 시작: {}", username);
            revertToPendingIfStillProcessing(requestId, serverName);
            throw new BusinessException(ErrorCode.POD_CREATION_CONCURRENCY_LIMIT);
        }

        return responseRef[0];
    }

    /**
     * approveRequest의 후처리(계정 생성 → Pod 생성 → DB 반영 → 메일)를 approvalExecutor
     * 스레드에서 실행한다. 각 단계의 실패는 기존과 동일하게 자체적으로 보상 트랜잭션을 수행하고
     * 조용히 종료한다 — 이 메서드를 호출한 쪽(관리자 HTTP 요청)은 이미 응답을 반환하고 떠난
     * 상태라 예외를 던져봐야 아무도 받지 않는다. 바깥 try/catch는 각 단계 내부에서 처리하지
     * 못한 예기치 않은 예외가 executor 스레드에서 조용히 사라지는 것을 막는 최후의 안전망이다.
     */
    private void processApproval(Long requestId, String username, UserCreationRequestDTO creationDto,
                                  ApproveRequestDTO dto, String serverName) {
        try {
            // 2. 외부 HTTP 호출
            UserCreationResponse userResponse;
            try {
                userResponse = callUserCreationApi(creationDto);
            } catch (Exception e) {
                log.warn("[보상 트랜잭션] 사용자 생성 실패 → 상태 복구 시작: {}", username, e);
                revertToPendingIfStillProcessing(requestId, serverName);
                return;
            }

            CreatePodResponseDTO podResponse;
            try {
                podResponse = podService.createPod(username);
            } catch (Exception e) {
                // BusinessException뿐 아니라 WebClient 타임아웃 등 예기치 않은 예외도
                // 여기서 잡아야 한다 — 안 그러면 바깥쪽 catch-all까지 새어나가 상태
                // 복구는 되어도 방금 만든 Ubuntu 계정이 정리되지 않은 채 남는다.
                log.warn("[보상 트랜잭션] Pod 생성 실패 → 계정 삭제 및 상태 복구 시작: {}", username, e);
                String failedNode = (e instanceof PodCreationFailedException pcfe) ? pcfe.getNode() : null;
                tryCompensateDeleteUser(username, failedNode, serverName);
                revertToPendingIfStillProcessing(requestId, serverName);
                return;
            }

            // 3. DB 저장 (새 트랜잭션)
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            final Request[] savedRequestRef = {null};
            try {
                tx.execute(status -> {
                    // 행 잠금 + 상태 재확인: 외부 호출(계정/Pod 생성) 도중 다른 관리자가 거절을
                    // 눌러 상태가 이미 바뀌었을 수 있다. 여기서 다시 확인하지 않고 무조건
                    // approve()로 덮어쓰면, 거절됐는데도 방금 만든 계정/Pod가 FULFILLED로
                    // 살아남는 정합성 문제가 생긴다 (rejectRequest도 findByIdForUpdate로
                    // 행 잠금을 쓰므로 여기서 걸리면 그 커밋이 끝난 뒤의 최신 상태를 본다).
                    Request req = requestRepository.findByIdForUpdate(requestId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
                    if (req.getStatus() != Status.PROCESSING) {
                        log.warn("[보상 트랜잭션] 승인 처리 중 상태가 변경됨(다른 관리자가 거절했을 수 있음) - " +
                                "requestId={}, 현재 상태={}", requestId, req.getStatus());
                        throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
                    }
                    ContainerImage image = containerImageRepository.findById(dto.imageId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
                    ResourceGroup rg = resourceGroupRepository.findById(dto.resourceGroupId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
                    req.assignUbuntuIds(userResponse.uid(), userResponse.gid());
                    req.approve(image, rg, dto.adminComment());
                    req.assignPodInfo(podResponse.podName(), podResponse.node());
                    for (CreatePodResponseDTO.PortInfo port : podResponse.ports()) {
                        podExternalPortRepository.save(PodExternalPort.builder()
                                .request(req)
                                .internalPort(port.internalPort())
                                .externalPort(port.externalPort())
                                .usagePurpose(port.usagePurpose())
                                .build());
                    }
                    // 트랜잭션 종료 후 사용되는 모든 lazy 연관 초기화
                    req.getUser().getEmail();
                    req.getContainerImage().getImageName();
                    req.getResourceGroup().getServerName();
                    req.getRequestGroups().size();
                    savedRequestRef[0] = req;
                    return null;
                });
            } catch (Exception e) {
                log.error("[보상 트랜잭션] DB 업데이트 실패 → 전체 infra 리소스 삭제 시작: {}", username, e);
                tryCompensateAll(username, podResponse.podName(), podResponse.node(), serverName);
                revertToPendingIfStillProcessing(requestId, serverName);
                return;
            }

            // 4. 이메일 발송 (트랜잭션 종료 후, 실패해도 Pod·계정은 이미 생성됨)
            Request savedRequest = savedRequestRef[0];
            String sshPort = extractExternalPort(podResponse, "ssh");
            String jupyterPort = extractExternalPort(podResponse, "jupyter");
            sendNotificationSafely(
                    () -> alarmService.sendContainerCreatedEmail(savedRequest, sshPort, jupyterPort),
                    () -> log.info("사용자 '{}'에게 컨테이너 배정 안내 메일을 발송했습니다.", savedRequest.getUser().getName()),
                    e -> log.warn("사용자 '{}'에게 배정 안내 메일 발송 실패. (RequestId: {})",
                            savedRequest.getUser().getName(), savedRequest.getRequestId(), e)
            );
        } catch (Exception e) {
            // executor 스레드에서 여기까지 예외가 올라오면 아무도 받지 않고 조용히 사라진다 —
            // 최소한 로그를 남기고 요청이 PROCESSING에 영구히 갇히지 않도록 상태를 복구한다.
            log.error("[승인 후처리] 예기치 않은 오류로 처리 중단 → 상태 복구 시도: {}", username, e);
            revertToPendingIfStillProcessing(requestId, serverName);
        }
    }

    private UserCreationResponse callUserCreationApi(UserCreationRequestDTO userCreationDto) {
        try {
            log.info("사용자 생성 API 호출 시작: {}", userCreationDto.username());
            UserCreationResponse userResponse = userCreationWebClient.put()
                    .uri("/accounts/users")
                    .bodyValue(userCreationDto)
                    .retrieve()
                    .onStatus(HttpStatus.BAD_REQUEST::equals, clientResponse ->
                            Mono.error(new BusinessException(ErrorCode.INVALID_USERNAME_FORMAT))
                    )
                    .onStatus(HttpStatus.CONFLICT::equals, clientResponse ->
                            Mono.error(new BusinessException(ErrorCode.USER_ALREADY_EXISTS))
                    )
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new BusinessException("사용자 생성 실패: " + body, ErrorCode.USER_CREATION_FAILED)))
                    )
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new BusinessException("사용자 생성 실패: " + body, ErrorCode.USER_CREATION_FAILED)))
                    )
                    .bodyToMono(UserCreationResponse.class)
                    .block();
            log.info("사용자 생성 성공: {}", userResponse);
            if (userResponse == null || userResponse.uid() == null || userResponse.gid() == null) {
                log.error("사용자 생성 API 응답에 UID/GID가 없습니다: {}", userResponse);
                throw new BusinessException(ErrorCode.UID_ALLOCATION_FAILED);
            }
            return userResponse;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("사용자 생성 API 호출 중 예기치 않은 오류 발생.", e);
            throw new BusinessException(ErrorCode.USER_CREATION_FAILED);
        }
    }

    /** podResponse 포트 목록에서 usage_purpose가 일치하는 첫 외부 포트(문자열). 없으면 "". */
    private String extractExternalPort(CreatePodResponseDTO podResponse, String purpose) {
        return podResponse.ports().stream()
                .filter(p -> purpose.equalsIgnoreCase(p.usagePurpose()))
                .map(p -> String.valueOf(p.externalPort()))
                .findFirst().orElse("");
    }

    @Transactional
    public SaveRequestResponseDTO rejectRequest(RejectRequestDTO dto) {
        // 행 잠금: PROCESSING 상태를 거절하는 동안 approveRequest의 마지막 DB 반영
        // 트랜잭션과 순서가 뒤섞이지 않게 한다. 잠금만으로는 충분하지 않아서
        // approveRequest 쪽에도 반영 직전 상태 재확인이 함께 필요하다 — 아래 참고.
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
        ChangeRequest changeRequest = changeRequestRepository.findById(dto.changeRequestId())
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

    @Transactional
    public void approveModification(Long adminId, ApproveModificationDTO dto) {
        // 행 잠금 조회: 동시에 같은 변경 요청을 승인 시도하는 두 번째 트랜잭션은 첫 트랜잭션 커밋까지 대기하다가
        // FULFILLED 상태를 보고 실패한다 (PORT 등 부수 효과의 중복 실행 방지)
        ChangeRequest changeRequest = changeRequestRepository.findByIdForUpdate(dto.changeRequestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (changeRequest.getStatus() != Status.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Request originalRequest = changeRequest.getRequest();
        if (originalRequest == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        // ChangeRequest는 FULFILLED 상태를 전제로 신청된다. 그 사이 원본 Request가 삭제되거나
        // 마이그레이션/재승인 처리 중으로 넘어갔는데 상태 확인 없이 그대로 적용하면, 이미 죽었거나
        // 다른 트랜잭션이 다루고 있는 Request의 필드를 조용히 덮어써 정합성이 깨진다.
        if (originalRequest.getStatus() != Status.FULFILLED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
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

        if (expiryChange != null) {
            sendNotificationSafely(
                    () -> alarmService.sendContainerExtendedEmail(originalRequest, expiryChange.oldExpiresAt(), expiryChange.newExpiresAt()),
                    () -> log.info("사용자 '{}'에게 기간 연장 안내 메일을 발송했습니다.", originalRequest.getUser().getName()),
                    e -> log.warn("기간 연장 안내 메일 발송 실패: changeRequestId={}", dto.changeRequestId(), e)
            );
        } else {
            sendNotificationSafely(
                    () -> alarmService.sendModificationApprovedEmail(changeRequest, dto.adminComment()),
                    () -> log.info("사용자 '{}'에게 변경 요청 승인 안내 메일을 발송했습니다.", originalRequest.getUser().getName()),
                    e -> log.warn("변경 요청 승인 안내 메일 발송 실패: changeRequestId={}", dto.changeRequestId(), e)
            );
        }
    }

    // ── approveModification: ChangeType별 적용 로직 ──────────────────────
    // Map으로 등록해두면 새 ChangeType이 추가될 때 이 메서드 자체를 수정하지 않고
    // applier 하나만 더 등록하면 된다 (개방-폐쇄 원칙).

    private Map<ChangeType, ChangeApplier> changeAppliers() {
        return Map.of(
                ChangeType.EXPIRES_AT, this::applyExpiresAtChange,
                ChangeType.GROUP, this::applyGroupChange,
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

    private ExpiryChangeResult applyGroupChange(Request originalRequest, String newValueJson) throws JsonProcessingException {
        // 그룹 변경은 복잡하기 때문에, 엔티티가 아닌 서비스 레이어에서 처리합니다.
        originalRequest.getRequestGroups().clear();
        Set<Long> newGroupIds = objectMapper.readValue(newValueJson,
                objectMapper.getTypeFactory().constructCollectionType(Set.class, Long.class));
        Set<Group> newGroups = newGroupIds.stream()
                .map(gid -> groupRepository.findByUbuntuGid(gid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)))
                .collect(Collectors.toSet());

        for (Group g : newGroups) {
            originalRequest.addGroup(g);
        }
        return null;
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

    private void tryCompensateDeleteUser(String username, String nodeName, String serverName) {
        try {
            ubuntuAccountService.deleteUbuntuAccount(username, nodeName);
            log.info("[보상 트랜잭션 완료] 계정 삭제: {}, node={}", username, nodeName);
        } catch (Exception e) {
            log.error("[보상 트랜잭션 실패] 계정 삭제 실패 - 수동 정리 필요: {}", username, e);
            alertCompensationFailure(String.format("[보상 트랜잭션 실패] 계정 삭제 실패 - 수동 정리 필요: username=%s", username), serverName);
        }
    }

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

    // approveRequest의 세 실패 지점(계정 생성 실패/Pod 생성 실패/DB 반영 실패) 모두 같은 문제를
    // 가진다: 실패는 두 가지 경우로 갈린다. ① 그 사이 다른 관리자가 거절해서 상태가 이미 DENIED
    // 등 최종 상태로 바뀐 경우(정상 동작 — 건드리지 않는다) ② 그 외 원인(외부 API 오류, 이미지/
    // 리소스그룹 조회 실패, 저장 오류 등)으로 여전히 PROCESSING인 채 실패한 경우. 상태 확인 없이
    // 무조건 PENDING으로 덮어쓰면 ①에서 다른 관리자의 거절 결정을 조용히 지워버리고, 반대로
    // ②를 그대로 두면 인프라(계정/Pod)는 이미 정리됐는데 요청은 재승인도 거절도 못 하는
    // PROCESSING 상태에 영구히 갇힌다. 그래서 세 지점 모두 락 + 상태 재확인을 거치는 이 메서드로
    // 통일한다. approveRequest 내부의 즉시 실패 보상뿐 아니라, admin_be 프로세스 자체가 승인
    // 처리 도중 죽어서(강제 재배포, OOM 등) catch 블록조차 실행 못 한 경우를 쓸어담는
    // RequestSchedulerService의 재조정(reconciliation) 잡에서도 재사용한다.
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

    private void tryCompensateAll(String username, String podName, String nodeName, String serverName) {
        try {
            podService.deletePod(podName);
            log.info("[보상 트랜잭션 완료] Pod 삭제: {}", podName);
        } catch (Exception e) {
            log.error("[보상 트랜잭션 실패] Pod 삭제 실패 - 수동 정리 필요: {}", podName, e);
            alertCompensationFailure(String.format("[보상 트랜잭션 실패] Pod 삭제 실패 - 수동 정리 필요: podName=%s", podName), serverName);
        }
        tryCompensateDeleteUser(username, nodeName, serverName);
    }

    record UserCreationResponse(
            String status,
            UserInfo user
    ) {
        Long uid() { return user != null ? user.uid() : null; }
        Long gid() { return user != null ? user.gid() : null; }

        record UserInfo(
                @JsonProperty("uid")
                @JsonAlias({"ubuntuUid", "ubuntu_uid"})
                Long uid,
                @JsonProperty("gid")
                @JsonAlias({"ubuntuGid", "ubuntu_gid"})
                Long gid
        ) {}
    }
}
