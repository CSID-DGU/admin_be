package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.users.dto.request.PasswordUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.PhoneUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UbuntuUsernameRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.MyInfoResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final PasswordEncoder passwordEncoder;

    private static final long UID_BASE = 10000; // TODO: 이부분 시스템에 맞추어서 수정하기

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
     * 단일 유저 조회
     */
    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(Long userId) {
        log.debug("[getUserById] userId={}", userId);
        return UserResponseDTO.fromEntity(userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND)));
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

    /**
     * 우분투 유저네임을 나중에 등록한다 — 이 필드가 생기기 전에 가입한 계정처럼
     * ubuntuUsername이 비어있으면 컨테이너 신청 자체가 막히는데(RequestCommandService.
     * createRequest), 그런 계정을 되살릴 방법이 이 메서드가 생기기 전엔 없었다(관리자
     * 쪽에도 별도 지정 API가 없었다). 가입 때와 동일하게 유일성을 검사하지만, 사전 검사와
     * 저장 사이의 경합은 unique 제약 위반을 잡아 같은 에러로 변환한다.
     *
     * findByIdForUpdate로 User 행을 잠근다 — 안 그러면 같은 사용자가 동시에 서로 다른
     * 유저네임 두 개를 보내는 요청(중복 클릭 등)이 둘 다 "아직 비어있음"을 보고 통과해,
     * 어느 쪽이 최종값이 될지 레이스가 생긴다.
     */
    public UserResponseDTO registerUbuntuUsername(Long userId, UbuntuUsernameRegisterRequestDTO request) {
        log.info("[registerUbuntuUsername] userId={} 우분투 유저네임 등록 시도: {}", userId, request.ubuntuUsername());

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));

        // 이미 자기 자신의 유저네임과 같은 값을 다시 보낸 경우, 아래 existsByUbuntuUsername이
        // 자기 자신과 충돌한 것처럼 보여 "이미 사용 중"이라는 오해를 주는 DUPLICATE_USERNAME을
        // 던진다 — 실제로는 "이미 등록되어 있어 변경 불가"가 맞는 이유이므로 먼저 구분한다.
        if (request.ubuntuUsername().equals(user.getUbuntuUsername())) {
            throw new BusinessException(ErrorCode.UBUNTU_USERNAME_ALREADY_ASSIGNED);
        }

        if (userRepository.existsByUbuntuUsername(request.ubuntuUsername())) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }

        user.registerUbuntuUsername(request.ubuntuUsername());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            log.warn("[registerUbuntuUsername] userId={} 유저네임 중복으로 등록 실패(경합)", userId);
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }

        log.info("[registerUbuntuUsername] userId={} 우분투 유저네임 등록 완료: {}", userId, request.ubuntuUsername());
        return UserResponseDTO.fromEntity(user);
    }

}
