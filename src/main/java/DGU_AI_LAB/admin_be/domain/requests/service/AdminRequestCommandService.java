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
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
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
    private final PodService podService;
    private final UbuntuAccountService ubuntuAccountService;
    private final PortRequestService portRequestService;
    private final ObjectMapper objectMapper;

    private final @Qualifier("configWebClient") WebClient userCreationWebClient;
    private final PlatformTransactionManager transactionManager;

    // 동시 처리 한도. Pod 생성 대기(최대 600초)가 그만큼 Tomcat 워커 스레드를 붙잡으므로,
    // 이 이상 몰리면 큐잉하지 않고 즉시 명확한 에러로 실패시킨다. Operational Baseline은
    // 동기식 순차 실행이 정의라 승인 후처리 전체를 별도 executor로 넘기지 않는다 — 계정
    // 생성부터 Pod 생성까지 관리자 HTTP 요청 스레드가 그대로 붙잡은 채 처리한다.
    private final Semaphore podCreationSemaphore = new Semaphore(3);

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
     * 사용 신청을 승인한다. Operational Baseline은 동기식 순차 실행이 정의라, 계정 생성부터
     * Pod 생성 대기(최대 720초)까지 관리자 HTTP 요청 스레드가 그대로 붙잡은 채 처리한다 —
     * 이 메서드가 정상 반환하면 승인이 완전히 끝난 것이고, 예외가 나면 실패한 것이다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SaveRequestResponseDTO approveRequest(ApproveRequestDTO dto) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 1. 상태 검증 + PROCESSING 전환 + HTTP 요청 데이터 추출 (짧은 트랜잭션, 이후 커넥션 반납)
        final Long[] userIdRef = {null};
        final String[] usernameRef = {null};
        final String[] serverNameRef = {null};
        final UserCreationRequestDTO[] creationDtoRef = {null};
        tx.execute(status -> {
            // 행 잠금 조회: 동시에 같은 요청을 승인 시도하는 두 번째 트랜잭션은 여기서 대기하다가
            // 첫 트랜잭션 커밋 후 PROCESSING 상태를 보고 아래에서 실패한다 (중복 승인/중복 provisioning 방지)
            Request req = requestRepository.findByIdForUpdate(dto.requestId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            if (req.getStatus() != Status.PENDING) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
            }
            req.markAsProcessing(); // 다른 관리자의 중복 승인 시도 차단
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
            userIdRef[0] = req.getUser().getUserId();
            usernameRef[0] = req.getUbuntuUsername();
            // 보상 트랜잭션 실패 알림을 관리자가 실제로 보는 farm/lab 채널로 보내기 위해
            // 승인 시점의 서버 구분을 미리 떼어둔다 (resourceGroup은 신청 시점부터 항상 존재).
            serverNameRef[0] = req.getResourceGroup().getServerName();
            return null;
        });
        Long requestId = dto.requestId();
        Long userId = userIdRef[0];
        String username = usernameRef[0];
        String serverName = serverNameRef[0];
        UserCreationRequestDTO creationDto = creationDtoRef[0];

        // 2. 계정 확인/생성. userId 단위로 직렬화한다 — 짧은 DB 트랜잭션 두 개(확인용/커밋용)
        // 사이에 config-server HTTP 호출이 끼어 있어, User 행을 커밋 시점에만 잠그는 것만으로는
        // 같은 사용자의 다른 신청이 동시에 승인되는 경합을 못 막는다.
        final Long uid;
        final Long gid;
        // 이번 승인에서 계정을 새로 만들었는지 — 뒤 단계가 실패했을 때 계정을 지워도
        // 되는지 판단하는 기준이다. 재사용한 계정을 지우면 이 사용자의 다른 컨테이너와
        // 홈 디렉터리까지 함께 날아간다.
        final boolean accountCreatedNow;
        synchronized (approvalLockFor(userId)) {
            final Long[] existingUidRef = {null};
            final Long[] existingGidRef = {null};
            new TransactionTemplate(transactionManager).execute(status -> {
                User owner = userRepository.findByIdForUpdate(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                if (owner.hasUbuntuAccount()) {
                    existingUidRef[0] = owner.getUbuntuUid();
                    existingGidRef[0] = owner.getUbuntuGid();
                }
                return null;
            });

            if (existingUidRef[0] != null) {
                // 이미 리눅스 계정이 있는 사용자면 계정 생성 API를 건너뛰고 이 UID/GID를
                // 그대로 재사용한다 — 그래야 새 컨테이너가 기존 홈 디렉터리를 그대로 물려받는다.
                // 같은 유저네임으로 다시 만들 수도 없고(config-server가 409), 다시 만들면
                // UID가 바뀌어 기존 홈 디렉터리의 소유권이 어긋난다.
                log.info("이미 우분투 계정을 보유한 사용자 — 계정 생성 API 호출 생략: username={}, uid={}", username, existingUidRef[0]);
                uid = existingUidRef[0];
                gid = existingGidRef[0];
                accountCreatedNow = false;
            } else {
                UserCreationResponse userResponse;
                try {
                    userResponse = callUserCreationApi(creationDto);
                } catch (Exception e) {
                    log.warn("[보상 트랜잭션] 사용자 생성 실패 → 상태 복구 시작: {}", username, e);
                    notifyApprovalFailure(String.format(
                            "[승인 실패] 사용자 생성 실패로 상태를 PENDING으로 되돌렸습니다: username=%s, requestId=%d, error=%s",
                            username, requestId, e.getMessage()), serverName);
                    revertToPendingIfStillProcessing(requestId, serverName);
                    throw e;
                }
                uid = userResponse.uid();
                gid = userResponse.gid();
                accountCreatedNow = true;

                // 계정 생성 성공 직후, Pod 생성(최대 10분 대기)에 들어가기 전에 UID/GID를
                // User에 즉시 커밋한다. 이걸 미루면(최종 DB 반영 트랜잭션에서만 하면) Pod 생성
                // 대기 중 프로세스가 죽었을 때(강제 재배포, OOM 등) User는 이 계정의 존재를
                // 전혀 모르는 채로 남는다 — 재승인 시 이 확인이 다시 "계정 없음"으로 판단해
                // 계정 생성 API를 또 호출하고, config-server가 409를 던져 이 신청은 영구히
                // 승인 불가 상태에 갇힌다. 리눅스 계정/홈 디렉터리/krb5 principal은 이미
                // 살아있는데 DB만 그 사실을 모르는 상태를 최대한 짧게 줄인다.
                try {
                    Long committedUid = uid;
                    Long committedGid = gid;
                    new TransactionTemplate(transactionManager).execute(status -> {
                        User owner = userRepository.findByIdForUpdate(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                        owner.assignUbuntuAccount(committedUid, committedGid);
                        return null;
                    });
                } catch (Exception e) {
                    // 계정은 이미 인프라에 존재하는데 그 사실을 User에 못 남겼다. 아직 어느
                    // farm 노드에도 배포된 게 없는 시점(Pod 생성 전)이라 node를 모른 채
                    // 삭제하면 무관한 동명 레거시 계정까지 지울 위험이 있으므로 삭제는
                    // 시도하지 않는다 — 알리고 수동 확인을 받는다.
                    log.error("[보상 트랜잭션] 계정 생성 직후 UID/GID를 User에 반영하지 못함 - 계정은 인프라에 존재, 수동 확인 필요: username={}", username, e);
                    notifyApprovalFailure(String.format(
                            "[승인 실패] 계정 생성 직후 UID/GID 반영 실패 - 계정은 인프라에 존재하나 DB에 미반영, 수동 확인 필요: username=%s, requestId=%d",
                            username, requestId), serverName);
                    revertToPendingIfStillProcessing(requestId, serverName);
                    throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
                }
            }
        }

        // 3. 이번 신청이 요구하는 그룹에 사용자를 추가한다. 계정을 새로 만든 경우
        // callUserCreationApi가 이미 이 신청의 그룹을 넣어 만들었지만, 이 호출도 멱등
        // 집합-추가라 다시 불러도 안전하다(config-server add_user_groups). 재사용
        // 계정은 애초에 계정 생성 API 자체를 건너뛰므로, 이 호출이 없으면 최초 승인
        // 때의 그룹 멤버십에 영구히 고정되고 이후 신청이 요구하는 다른 그룹엔 실제
        // 파일 접근 권한이 생기지 않는다 — 같은 사용자가 여러 그룹에 동시에 속하는
        // 건 정상이고(A그룹 신청과 B그룹 신청을 둘 다 승인받았다면 둘 다 유지),
        // config-server 쪽 API도 원래 있던 그룹에서 빼지 않고 더하기만 한다.
        List<String> requestGroupNames = creationDto.supplementaryGroups().stream()
                .map(UserCreationRequestDTO.SupplementaryGroup::name)
                .toList();
        try {
            groupService.addUserToGroups(username, requestGroupNames);
        } catch (Exception e) {
            log.warn("[보상 트랜잭션] 그룹 추가 실패 → 상태 복구 시작: {}", username, e);
            notifyApprovalFailure(String.format(
                    "[승인 실패] 그룹 추가 실패로 상태를 PENDING으로 되돌렸습니다: username=%s, requestId=%d, groups=%s, error=%s",
                    username, requestId, requestGroupNames, e.getMessage()), serverName);
            if (accountCreatedNow) {
                // 아직 어느 farm 노드에도 배포된 게 없는 시점이라 node를 모른 채 삭제하면
                // 무관한 동명 레거시 계정까지 지울 위험이 있다 — tryCompensateDeleteUser
                // 내부에서 이 경우 삭제 대신 보류+알림으로 처리한다.
                tryCompensateDeleteUser(userId, username, null, serverName);
            }
            revertToPendingIfStillProcessing(requestId, serverName);
            throw e;
        }

        // 4. Pod 생성. 동시 처리 한도를 넘으면 큐잉하지 않고 즉시 실패시킨다 — 안 그러면
        // Tomcat 워커 스레드가 Pod 생성 대기(최대 600초)만큼씩 계속 쌓인다.
        CreatePodResponseDTO podResponse;
        try {
            if (!podCreationSemaphore.tryAcquire()) {
                throw new BusinessException(ErrorCode.POD_CREATION_CONCURRENCY_LIMIT);
            }
            try {
                podResponse = podService.createPod(username, requestId);
            } finally {
                podCreationSemaphore.release();
            }
        } catch (Exception e) {
            // BusinessException뿐 아니라 WebClient 타임아웃 등 예기치 않은 예외도
            // 여기서 잡아야 한다 — 안 그러면 상태 복구는 되어도 방금 만든 Ubuntu 계정이
            // 정리되지 않은 채 남는다.
            log.warn("[보상 트랜잭션] Pod 생성 실패 → 상태 복구 시작: {}", username, e);
            String failedNode = (e instanceof PodCreationFailedException pcfe) ? pcfe.getNode() : null;
            notifyApprovalFailure(String.format(
                    "[승인 실패] Pod 생성 실패로 상태를 PENDING으로 되돌렸습니다: username=%s, requestId=%d, error=%s",
                    username, requestId, e.getMessage()), serverName);
            if (accountCreatedNow) {
                tryCompensateDeleteUser(userId, username, failedNode, serverName);
            }
            revertToPendingIfStillProcessing(requestId, serverName);
            throw e;
        }

        // 5. DB 저장 (새 트랜잭션, HTTP 완료 후 짧게만 커넥션 보유)
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
                // UID/GID의 소유자는 신청이 아니라 웹 계정이다. 같은 사용자의 다른 신청이
                // 동시에 승인돼 먼저 배정했을 수 있으므로 User 행을 잠그고 배정한다 —
                // 같은 값이면 그대로 통과하고, 다른 값이면 여기서 실패해 보상 트랜잭션을 탄다.
                User owner = userRepository.findByIdForUpdate(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                owner.assignUbuntuAccount(uid, gid);
                req.assignUbuntuIds(uid, gid);
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
            log.error("[보상 트랜잭션] DB 업데이트 실패 → infra 리소스 삭제 시작: {}", username, e);
            notifyApprovalFailure(String.format(
                    "[승인 실패] DB 반영 실패로 infra 리소스 정리 후 상태를 PENDING으로 되돌렸습니다: username=%s, requestId=%d, error=%s",
                    username, requestId, e.getMessage()), serverName);
            tryCompensateAll(userId, username, podResponse.podName(), podResponse.node(), serverName, accountCreatedNow);
            revertToPendingIfStillProcessing(requestId, serverName);
            throw e;
        }

        // 6. 이메일 발송 (트랜잭션 종료 후, 실패해도 Pod·계정은 이미 생성됨)
        Request savedRequest = savedRequestRef[0];
        String sshPort = extractExternalPort(podResponse, "ssh");
        String jupyterPort = extractExternalPort(podResponse, "jupyter");
        sendNotificationSafely(
                () -> alarmService.sendContainerCreatedEmail(savedRequest, sshPort, jupyterPort),
                () -> log.info("사용자 '{}'에게 컨테이너 배정 안내 메일을 발송했습니다.", savedRequest.getUser().getName()),
                e -> log.warn("사용자 '{}'에게 배정 안내 메일 발송 실패. (RequestId: {})",
                        savedRequest.getUser().getName(), savedRequest.getRequestId(), e)
        );

        return SaveRequestResponseDTO.fromEntity(savedRequest);
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

        // DB에만 반영하고 끝내면 실제 리눅스 계정의 그룹 멤버십은 그대로다 — 이 그룹이
        // 요구하는 파일에 실제로 접근이 안 되는 채로 "변경 승인됨"만 표시되는 상태가 된다.
        // config-server의 그룹 추가는 집합-추가 방식이라(원래 그룹에서 빼지 않음) 여기서도
        // 새로 추가된 그룹만 보내면 된다. approveModification 전체가 하나의 트랜잭션이라
        // 이 안에서 외부 호출을 하면 그 시간만큼 커넥션을 붙들지만, 그룹 변경 자체가
        // 드문 오퍼레이션이라 승인 흐름 전체를 다시 3단계로 쪼갤 정도는 아니라고 판단했다.
        List<String> newGroupNames = newGroups.stream().map(Group::getGroupName).toList();
        groupService.addUserToGroups(originalRequest.getUbuntuUsername(), newGroupNames);
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

    /**
     * @param nodeName 이 계정의 krb5 keytab이 실제로 배포된(또는 배포를 시도한) farm 노드.
     *                 null이면(노드 선택 전에 실패했거나 응답에서 노드를 못 뽑은 경우) 삭제를
     *                 시도하지 않는다 — node_name 없이 삭제를 호출하면 config-server가 설정된
     *                 모든 farm 노드를 무차별로 훑어서, 같은 유저네임을 쓰는 무관한 레거시
     *                 계정까지 잘못 지울 수 있다(AdminUserService.deleteUbuntuAccount와 동일
     *                 원칙 — a3a8e21에서 사용자 삭제 경로에 먼저 적용됐던 가드를 여기도 맞춘다).
     *                 이 경우 계정과 User의 UID/GID는 그대로 남기고 알림만 보낸다 — 남은 계정은
     *                 재승인 시 hasUbuntuAccount() 분기로 자연스럽게 재사용된다.
     */
    private void tryCompensateDeleteUser(Long userId, String username, String nodeName, String serverName) {
        if (nodeName == null) {
            log.error("[보상 트랜잭션] 계정이 배포된 farm 노드를 알 수 없어 계정 삭제를 보류합니다 - 수동 정리 필요: username={}", username);
            alertCompensationFailure(String.format(
                    "[보상 트랜잭션] farm 노드 미상으로 계정 삭제 보류 - 계정/UID는 보존됩니다(재승인 시 재사용): username=%s", username), serverName);
            return;
        }
        try {
            ubuntuAccountService.deleteUbuntuAccount(username, nodeName);
            releaseUserUbuntuAccount(userId);
            log.info("[보상 트랜잭션 완료] 계정 삭제: {}, node={}", username, nodeName);
        } catch (Exception e) {
            log.error("[보상 트랜잭션 실패] 계정 삭제 실패 - 수동 정리 필요: {}", username, e);
            alertCompensationFailure(String.format("[보상 트랜잭션 실패] 계정 삭제 실패 - 수동 정리 필요: username=%s", username), serverName);
        }
    }

    /** 계정 삭제가 실제로 성공했을 때만 호출한다 — User에 남은 UID/GID를 비워, 다음 승인이
     *  이미 삭제된 계정을 "보유 중"으로 착각해 재사용을 시도하지 않게 한다. */
    private void releaseUserUbuntuAccount(Long userId) {
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                userRepository.findByIdForUpdate(userId).ifPresent(User::releaseUbuntuAccount);
                return null;
            });
        } catch (Exception e) {
            log.error("[보상 트랜잭션] User UID/GID 회수 실패 - 수동 확인 필요: userId={}", userId, e);
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

    // approveRequest가 동기식이라 실패는 이 메서드를 호출한 관리자에게 HTTP 4xx/5xx로도
    // 곧바로 보인다. 그래도 Slack에 별도로 남기는 이유는, 그 관리자가 응답을 못 받고
    // 창을 닫아버렸거나(타임아웃 등) 다른 관리자가 나중에 신청 목록만 보고 원인을 모른 채
    // 재승인을 시도하는 상황에서도 실패 이력이 남아있게 하기 위함이다.
    // (보상 자체의 실패가 아니라 원래 승인 처리의 실패를 알린다는 점만 alertCompensationFailure와 다르다.)
    private void notifyApprovalFailure(String message, String serverName) {
        try {
            alarmService.sendAdminSlackNotification(serverName, message);
        } catch (Exception ignored) {
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

    /**
     * @param accountCreatedNow 이번 승인에서 리눅스 계정을 새로 만들었는지. 기존 계정을
     *                          재사용한 경우에는 절대 지우면 안 된다 — 그 계정은 이 신청이
     *                          아니라 웹 계정에 귀속돼 있고, 사용자의 홈 디렉터리도 함께 사라진다.
     */
    private void tryCompensateAll(Long userId, String username, String podName, String nodeName, String serverName, boolean accountCreatedNow) {
        try {
            podService.deletePod(podName);
            log.info("[보상 트랜잭션 완료] Pod 삭제: {}", podName);
        } catch (Exception e) {
            log.error("[보상 트랜잭션 실패] Pod 삭제 실패 - 수동 정리 필요: {}", podName, e);
            alertCompensationFailure(String.format("[보상 트랜잭션 실패] Pod 삭제 실패 - 수동 정리 필요: podName=%s", podName), serverName);
        }
        if (accountCreatedNow) {
            tryCompensateDeleteUser(userId, username, nodeName, serverName);
        }
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
