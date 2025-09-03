package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    private final @Qualifier("pvcWebClient") WebClient userWebClient;

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

        if (!userRequests.isEmpty()) {
            userRequests.forEach(request -> deleteUbuntuAccount(request.getUbuntuUsername()));
            log.info("[deleteUser] userId={}와 연결된 모든 Request의 외부 계정 삭제 요청 완료", userId);
        } else {
            log.info("[deleteUser] userId={}와 연결된 Request가 없습니다. 외부 계정 삭제 요청을 건너뜁니다.", userId);
        }

        user.updateUserInfo(null, false);
        log.info("[deleteUser] userId={} 논리적 삭제 완료 (isActive=false)", userId);
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
        log.info("[updateUser] userId={} 정보 수정 완료", userId);
        return UserResponseDTO.fromEntity(user);
    }

    /**
     * username으로 우분투 계정 삭제
     * Config Server에 요청을 보내 실제로 ubuntu 계정을 삭제하고, DB의 Request 엔티티 Status를 DELETED로 변경합니다.
     */
    public void deleteUbuntuAccount(String username) {
        log.warn("[deleteUbuntuAccount] 우분투 계정 삭제 시도: {}", username);

        Request request = requestRepository.findByUbuntuUsername(username)
                .orElseThrow(() -> {
                    log.warn("[deleteUbuntuAccount] {}에 해당하는 Request가 없습니다.", username);
                    return new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND);
                });

        try {
            log.info("Starting Config Server API call to delete ubuntu account: {}", username);
            userWebClient.post()
                    .uri("/accounts/deleteuser/{username}", username)
                    .retrieve()
                    .onStatus(HttpStatus.BAD_REQUEST::equals, clientResponse ->
                            Mono.error(new BusinessException(ErrorCode.INVALID_USERNAME_FORMAT))
                    )
                    .onStatus(HttpStatus.NOT_FOUND::equals, clientResponse -> {
                        log.warn("외부 서버에 {} 계정이 이미 존재하지 않아 삭제가 불필요합니다.", username);
                        return Mono.empty();
                    })
                    .onStatus(WebClientResponseException.class::isInstance, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new BusinessException("우분투 계정 삭제 실패: " + body, ErrorCode.UBUNTU_USER_DELETION_FAILED)))
                    )
                    .toBodilessEntity()
                    .block();

            log.info("Config Server's deletion successful for account: {}", username);

            request.delete();
            requestRepository.save(request);
            log.info("[deleteUbuntuAccount] {}에 대한 Request 정보 상태를 DELETED로 변경 완료", username);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("An unexpected error occurred during Config Server's account deletion.", e);
            throw new BusinessException("우분투 계정 삭제 중 예기치 않은 오류 발생.", ErrorCode.UBUNTU_USER_DELETION_FAILED);
        }
    }
}