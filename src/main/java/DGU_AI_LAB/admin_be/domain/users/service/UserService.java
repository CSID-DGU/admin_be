package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.users.dto.request.PasswordUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.PhoneUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserCreateRequestDTO;
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