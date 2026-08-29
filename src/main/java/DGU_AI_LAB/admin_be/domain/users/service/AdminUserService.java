package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.UbuntuAccountService;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.ConflictException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    private final UbuntuAccountService ubuntuAccountService;
    private final AlarmService alarmService;
    private final PodExternalPortRepository podExternalPortRepository;
    private final MessageUtils messageUtils;
    private final TokenService tokenService;

    /**
     * 전체 유저 조회
     */
    @Transactional(readOnly = true)
    public List<UserSummaryDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserSummaryDTO::fromEntity)
                .toList();
    }

    /**
     * 유저 삭제 (soft delete 적용)
     * 이와 동시에 해당 유저가 소유한 모든 Request(우분투 계정)의 상태를 'DELETED'로 변경하고 외부 시스템에서도 삭제합니다.
     */
    @Transactional
    public void deleteUser(Long userId) {
        log.warn("[deleteUser] userId={} 논리적 삭제 시도", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("[deleteUser] userId={} 존재하지 않음", userId);
                    return new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND);
                });

        List<Request> userRequests = requestRepository.findAllByUser(user);

        boolean hasMigratingRequest = userRequests.stream()
                .anyMatch(r -> r.getStatus() == Status.MIGRATING);
        if (hasMigratingRequest) {
            // 마이그레이션 진행 중인 요청이 하나라도 있으면 유저 탈퇴 자체를 거부한다.
            // 여기서 나머지 Request만 정리하고 유저를 탈퇴시키면, 마이그레이션 결과가 반영될
            // Request의 소유자가 이미 비활성화된 상태로 남아 정합성이 깨진다.
            log.warn("[deleteUser] userId={} 마이그레이션 진행 중인 요청이 있어 삭제를 거부합니다.", userId);
            throw new ConflictException(ErrorCode.REQUEST_MIGRATION_IN_PROGRESS);
        }

        List<Long> fulfilledIds = userRequests.stream()
                .filter(r -> r.getStatus() == Status.FULFILLED)
                .map(Request::getRequestId)
                .collect(Collectors.toList());
        Map<Long, List<PodExternalPort>> portsMap = fulfilledIds.isEmpty()
                ? Map.of()
                : podExternalPortRepository
                        .findByRequestRequestIdIn(fulfilledIds)
                        .stream()
                        .collect(Collectors.groupingBy(p -> p.getRequest().getRequestId()));

        for (Request request : userRequests) {
            if (fulfilledIds.contains(request.getRequestId())) {
                ubuntuAccountService.deleteUbuntuAccount(request.getUbuntuUsername());
                request.deleteAfterCleanup();
                requestRepository.save(request);
                try {
                    List<PodExternalPort> ports = portsMap.getOrDefault(request.getRequestId(), List.of());
                    alarmService.sendContainerDeletedEmail(request, ports);
                } catch (Exception e) {
                    log.warn("[deleteUser] 삭제 안내 메일 발송 실패: ubuntuUsername={}", request.getUbuntuUsername(), e);
                }
            } else if (request.getStatus() != Status.DELETED) {
                request.delete();
            }
        }
        log.info("[deleteUser] userId={}와 연결된 Request 정리 완료", userId);

        user.withdraw();
        // 남아있는 리프레시 토큰으로 액세스 토큰을 계속 재발급받지 못하도록 함께 폐기한다.
        tokenService.logout(userId);
        log.info("[deleteUser] userId={} 논리적 삭제 완료 (isActive=false)", userId);

        try {
            String subject = messageUtils.get("notification.user.admin-delete.subject");
            String body = messageUtils.get("notification.user.admin-delete.body", user.getName());
            alarmService.sendAllAlerts(user.getName(), user.getEmail(), subject, body);
        } catch (Exception e) {
            log.warn("[deleteUser] 계정 비활성화 안내 메일 발송 실패: userId={}", userId, e);
        }
    }

    /**
     * 단독 우분투 계정 삭제 (컨트롤러 엔드포인트용)
     * FULFILLED 상태인 Request를 username으로 찾아 외부 계정 삭제 후 DB 상태를 DELETED로 변경한다.
     */
    @Transactional
    public void deleteUbuntuAccount(String username) {
        log.warn("[deleteUbuntuAccount] 우분투 계정 삭제 시도: {}", username);
        Request request = requestRepository.findByUbuntuUsername(username)
                .orElseThrow(() -> {
                    log.warn("[deleteUbuntuAccount] {}에 해당하는 Request가 없습니다.", username);
                    return new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND);
                });

        if (request.getStatus() == Status.DELETED) {
            log.warn("[deleteUbuntuAccount] {}은 이미 DELETED 상태입니다.", username);
            throw new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND);
        }

        ubuntuAccountService.deleteUbuntuAccount(username);
        request.deleteAfterCleanup();
        requestRepository.save(request);
        log.info("[deleteUbuntuAccount] {} 계정 삭제 및 DB 상태 업데이트 완료", username);
        try {
            alarmService.sendContainerDeletedEmail(request);
        } catch (Exception e) {
            log.warn("[deleteUbuntuAccount] 삭제 안내 메일 발송 실패: ubuntuUsername={}", username, e);
        }
    }

    /**
     * 비활성화된 유저 재활성화
     */
    @Transactional
    public UserSummaryDTO reactivateUser(Long userId) {
        log.info("[reactivateUser] userId={} 재활성화 시도", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));

        if (user.getIsActive()) {
            log.warn("[reactivateUser] userId={} 이미 활성화 상태", userId);
            throw new ConflictException(ErrorCode.USER_ALREADY_ACTIVE);
        }

        user.reactivate();
        log.info("[reactivateUser] userId={} 재활성화 완료", userId);
        return UserSummaryDTO.fromEntity(user);
    }

    /**
     * 유저 정보 수정
     */
    @Transactional
    public UserResponseDTO updateUser(Long userId, UserUpdateRequestDTO request) {
        log.info("[updateUser] userId={} 정보 수정 시작", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
        user.updateUserInfo(request.password(), request.isActive());
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            // 비활성화 처리된 계정은 리프레시 토큰도 함께 폐기한다.
            tokenService.logout(userId);
        }
        log.info("[updateUser] userId={} 정보 수정 완료", userId);
        return UserResponseDTO.fromEntity(user);
    }
}
