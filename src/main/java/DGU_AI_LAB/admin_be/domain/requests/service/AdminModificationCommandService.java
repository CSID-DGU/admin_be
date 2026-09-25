package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupService;
import DGU_AI_LAB.admin_be.domain.portRequests.service.PortRequestService;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.PortRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectModificationDTO;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 사용 중인 신청(FULFILLED)에 대한 변경 요청(ChangeRequest)의 승인·거절. 새 신청의 승인·거절은
 * {@link AdminRequestCommandService}가 맡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminModificationCommandService {

    private final AlarmService alarmService;

    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ContainerImageRepository containerImageRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final GroupRepository groupRepository;
    private final GroupService groupService;
    private final PortRequestService portRequestService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

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
     * 그래서 AdminRequestCommandService.approveRequest와 같은 3단계 패턴을 쓴다: ①잠금+검증+그룹 해석(트랜잭션) →
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

    // 관리자가 실제로 보는 farm/lab 채널로 알린다. 알림 실패가 원래 예외 전파를 막으면 안 된다.
    private void notifyApprovalFailure(String message, String serverName) {
        try {
            alarmService.sendAdminSlackNotification(serverName, message);
        } catch (Exception ignored) {
        }
    }
}
