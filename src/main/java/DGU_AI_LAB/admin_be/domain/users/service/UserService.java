package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserCreateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.MyInfoResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final long UID_BASE = 10000; // TODO: 이부분 시스템에 맞추어서 수정하기

    /**
     * 유저 생성
     */
    public UserResponseDTO createUser(UserCreateRequestDTO request) {
        log.info("[createUser] name={}", request.name());

        Long uid = getNextAvailableUid();
        Long gid = uid; // 기본적으로 UID와 동일한 GID 사용

        Group group = groupRepository.findById(gid)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));

        User user = request.toEntity();
        user.updateUbuntuUid(uid);
        user.updateUbuntuGroup(group);

        User saved = userRepository.save(user);
        log.info("[createUser] user created with userId={}", saved.getUserId());

        return UserResponseDTO.fromEntity(saved);
    }

    private Long getNextAvailableUid() {
        return userRepository.findMaxUbuntuUid().orElse(UID_BASE - 1) + 1;
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
}
