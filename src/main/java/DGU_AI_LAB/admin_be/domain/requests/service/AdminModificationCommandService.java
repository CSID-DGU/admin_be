package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupOperationService;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.util.AfterCommit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

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
    private final ChangeRequestRepository changeRequestRepository;
    private final GroupOperationService groupOperationService;
    private final ObjectMapper objectMapper;

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
        AfterCommit.run("변경 요청 거절 메일, changeRequestId " + dto.changeRequestId(),
                () -> alarmService.sendModificationRejectedEmail(changeRequest, dto.adminComment()));
    }

    /**
     * 변경 요청을 승인한다. EXPIRES_AT 은 DB 값만 바꾸면 되므로 이 트랜잭션에서 끝난다.
     *
     * <p>GROUP 은 AD·계정 원장·떠 있는 컨테이너까지 바꿔야 해서 작업으로 등록만 하고 변경 요청을 반영 중(PROCESSING)
     * 으로 둔 채 돌아온다. 사용자 그룹 기록과 승인 완료(FULFILLED)·안내 메일은 작업이 성공한 뒤
     * {@link GroupOperationService#complete}가 한다. 작업이 실패하면 변경 요청은 승인 대기로 돌아온다.
     */
    @Transactional
    public void approveModification(Long adminId, ApproveModificationDTO dto) {
        // 행 잠금 조회: 동시에 같은 변경 요청을 승인 시도하는 두 번째 트랜잭션은 첫 트랜잭션 커밋까지 대기하다가
        // PENDING 이 아닌 상태를 보고 실패한다 (GROUP 작업 등록 등 부수 효과의 중복 실행 방지)
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
            groupOperationService.startAdd(changeRequest, originalRequest, admin, dto.adminComment());
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

        // 메일은 커밋 뒤에 보낸다 — 두 행을 잠근 채 보내지 않고, 롤백된 승인을 알리지 않는다.
        if (expiryChange != null) {
            AfterCommit.run("기간 연장 안내 메일, changeRequestId " + dto.changeRequestId(), () -> {
                alarmService.sendContainerExtendedEmail(originalRequest, expiryChange.oldExpiresAt(), expiryChange.newExpiresAt());
                log.info("사용자 '{}'에게 기간 연장 안내 메일을 발송했습니다.", originalRequest.getUser().getName());
            });
        } else {
            AfterCommit.run("변경 요청 승인 안내 메일, changeRequestId " + dto.changeRequestId(), () -> {
                alarmService.sendModificationApprovedEmail(changeRequest, dto.adminComment());
                log.info("사용자 '{}'에게 변경 요청 승인 안내 메일을 발송했습니다.", originalRequest.getUser().getName());
            });
        }
    }

    // ── approveModification: ChangeType별 적용 로직 ──────────────────────
    // Map으로 등록해두면 새 ChangeType이 추가될 때 이 메서드 자체를 수정하지 않고
    // applier 하나만 더 등록하면 된다 (개방-폐쇄 원칙).

    // GROUP은 작업으로 등록해 반영하므로 이 맵을 거치지 않고 approveModification에서 직접 분기한다
    // — 여기 등록하면 죽은 코드가 된다.
    // RESOURCE_GROUP·CONTAINER_IMAGE·PORT는 DB 값만 바꾸고 떠 있는 Pod에는 반영하지 못해 등록하지 않는다
    // (SingleChangeRequestDTO.SUPPORTED_TYPES). 예전에 들어온 요청은 UNSUPPORTED_CHANGE_TYPE으로 막히고 거절만 할 수 있다.
    private Map<ChangeType, ChangeApplier> changeAppliers() {
        return Map.of(ChangeType.EXPIRES_AT, this::applyExpiresAtChange);
    }

    private ExpiryChangeResult applyExpiresAtChange(Request originalRequest, String newValueJson) throws JsonProcessingException {
        LocalDateTime newExpiresAt = LocalDateTime.parse(objectMapper.readValue(newValueJson, String.class));
        // 요청할 때 미래였어도 승인이 늦으면 이미 지났을 수 있다. 그대로 반영하면 다음 만료 정리에서
        // 컨테이너가 바로 삭제된다 — 연장 요청이 삭제로 바뀌는 셈이라 승인하지 않는다.
        if (!newExpiresAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_EXPIRES_AT_PASSED);
        }
        LocalDateTime oldExpiresAt = originalRequest.getExpiresAt();
        originalRequest.updateExpiresAt(newExpiresAt);
        return new ExpiryChangeResult(oldExpiresAt, newExpiresAt);
    }

    @FunctionalInterface
    private interface ChangeApplier {
        /** newValueJson을 파싱해 originalRequest에 반영한다. EXPIRES_AT 변경일 때만 이전/이후 만료일을 담아 반환한다. */
        ExpiryChangeResult apply(Request originalRequest, String newValueJson) throws JsonProcessingException;
    }

    private record ExpiryChangeResult(LocalDateTime oldExpiresAt, LocalDateTime newExpiresAt) {}
}
