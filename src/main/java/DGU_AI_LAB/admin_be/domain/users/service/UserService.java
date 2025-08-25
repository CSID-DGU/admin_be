package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserAuthRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.PasswordUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.PhoneUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.MyInfoResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserAuthResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import DGU_AI_LAB.admin_be.global.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final RequestRepository requestRepository;
    private final PasswordEncoder passwordEncoder;

    private static final long UID_BASE = 10000; // TODO: 이부분 시스템에 맞추어서 수정하기

    /**
     * 단일 유저 조회
     */
    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(Long userId) {
        log.debug("[getUserById] userId={}", userId);
        return UserResponseDTO.fromEntity(userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND)));
    }

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
     * 유저 단일 조회
     */
    @Transactional(readOnly = true)
    public MyInfoResponseDTO getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
        return MyInfoResponseDTO.fromEntity(user);
    }

    /** ssh 로그인 */
    @Transactional
    public UserAuthResponseDTO userAuth(UserAuthRequestDTO dto) {
        // 1. 로그인 인증 처리 시작 로그
        log.info("사용자 인증을 시작합니다. username: {}", dto.username());

        // 2. 비밀번호 암호화
        String encodedPassword = PasswordUtil.encodePassword(dto.passwordBase64());
        log.debug("입력된 비밀번호가 성공적으로 암호화되었습니다. username: {}", dto.username());

        // 3. 사용자 및 비밀번호 일치 여부 확인
        Request request = requestRepository.findByUbuntuUsernameAndUbuntuPassword(dto.username(), encodedPassword)
                .orElseThrow(() -> {
                    // 3-1. 사용자 정보를 찾을 수 없을 때의 로그
                    log.warn("사용자 '{}'를 찾을 수 없거나 비밀번호가 일치하지 않습니다.", dto.username());
                    return new UnauthorizedException(ErrorCode.USER_NOT_FOUND);
                });

        // 4. 추가 비밀번호 일치 확인 (Optional: Optional로 처리되었기 때문에 이중 확인)
        if (!encodedPassword.equals(request.getUbuntuPassword())) {
            // 4-1. 비밀번호 불일치 로그
            log.error("사용자 '{}'에 대해 데이터베이스 비밀번호와 암호화된 비밀번호가 일치하지 않습니다. (내부 로직 오류 가능성)", dto.username());
            throw new UnauthorizedException(ErrorCode.INVALID_LOGIN_INFO);
        }

        // 5. 인증 성공 로그
        log.info("사용자 '{}'의 인증이 성공적으로 완료되었습니다.", dto.username());

        return new UserAuthResponseDTO(true, request.getUbuntuUsername());
    }

    /**
     * 사용자 비밀번호 변경
     */
    public UserResponseDTO updatePassword(Long userId, PasswordUpdateRequestDTO request) {
        log.info("[updatePassword] userId={} 비밀번호 변경 시도", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND)); // ⭐ USER_NOT_FOUND 사용

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            log.warn("[updatePassword] userId={} 현재 비밀번호 불일치", userId);
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        // 새 비밀번호가 현재 비밀번호와 동일한지 확인
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            log.warn("[updatePassword] userId={} 새 비밀번호가 현재 비밀번호와 동일", userId);
            throw new BusinessException(ErrorCode.PASSWORD_CHANGE_SAME_AS_OLD);
        }

        // 새 비밀번호 암호화 및 업데이트
        String encodedNewPassword = passwordEncoder.encode(request.newPassword());
        user.updatePassword(encodedNewPassword);
        log.info("[updatePassword] userId={} 비밀번호 변경 완료", userId);
        return UserResponseDTO.fromEntity(user);
    }

    /**
     * 사용자 연락처 변경
     */
    public UserResponseDTO updatePhone(Long userId, PhoneUpdateRequestDTO request) {
        log.info("[updatePhone] userId={} 연락처 변경 시도", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
        user.updatePhone(request.newPhone());
        log.info("[updatePhone] userId={} 연락처 변경 완료", userId);
        return UserResponseDTO.fromEntity(user);
    }
}
