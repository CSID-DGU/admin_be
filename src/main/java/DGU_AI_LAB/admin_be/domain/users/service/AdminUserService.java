package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.MyInfoResponseDTO;
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
     * 유저 삭제
     */
    public void deleteUser(Long userId) {
        log.warn("[deleteUser] userId={} 삭제 시도", userId);
        if (!userRepository.existsById(userId)) {
            log.error("[deleteUser] userId={} 존재하지 않음", userId);
            throw new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND);
        }
        userRepository.deleteById(userId);
        log.info("[deleteUser] userId={} 삭제 완료", userId);
    }

    /**
     * 유저 정보 수정
     */
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
     */
    public void deleteUbuntuAccount(String username) {
        log.warn("[deleteUbuntuAccount] 우분투 계정 삭제 시도: {}", username);

        Request request = requestRepository.findByUbuntuUsername(username)
                .orElseThrow(() -> {
                    log.warn("[deleteUbuntuAccount] {}에 해당하는 Request가 없습니다.", username);
                    return new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND);
                });

        try {
            log.info("Starting external API call to delete ubuntu account: {}", username);

            userWebClient.delete()
                    .uri("/accounts/users/{username}", username)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("External account deletion successful for account: {}", username);

            requestRepository.delete(request);
            log.info("[deleteUbuntuAccount] {}에 대한 Request 정보 삭제 완료", username);

        } catch (WebClientResponseException e) {
            log.error("External account deletion failed with status: {}, response body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);

            // 외부 API가 404를 반환하면, 클라이언트에도 404를 반환하도록 한다.
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
            }
            // 404 이외의 다른 WebClient 에러는 기존 에러로 처리한다.
            throw new BusinessException(ErrorCode.UBUNTU_USER_DELETION_FAILED);

        } catch (Exception e) {
            log.error("An unexpected error occurred during external account deletion.", e);
            throw new BusinessException(ErrorCode.UBUNTU_USER_DELETION_FAILED);
        }
    }
}
