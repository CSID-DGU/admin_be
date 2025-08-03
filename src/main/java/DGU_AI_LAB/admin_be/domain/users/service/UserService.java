package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UserCreateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.UserGroup;
import DGU_AI_LAB.admin_be.domain.users.repository.UserGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.error.exception.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import DGU_AI_LAB.admin_be.domain.users.repository.UsedIdRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.UsedId;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UsedIdRepository usedIdRepository;
    private final UserRepository userRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final UserGroupRepository userGroupRepository;

    private static final long UID_BASE = 10000;

    /**
     * 유저 생성
     */
    @Transactional
    public UserResponseDTO createUser(UserCreateRequestDTO request) {
        log.info("[createUser] name={}", request.name());

        /* ResourceGroup resourceGroup = resourceGroupRepository.findById(request.resourceGroupId())
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));*/

        User user = request.toEntity();
        User saved = userRepository.save(user);

        log.info("[createUser] user created with userId={}", saved.getUserId());
        return UserResponseDTO.fromEntity(saved);
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
    public List<UserResponseDTO> getAllUsers() {
        log.debug("[getAllUsers] 전체 유저 조회 시작");
        return userRepository.findAll().stream()
                .map(UserResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 유저 삭제
     */
    @Transactional
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
    @Transactional
    public UserResponseDTO updateUser(Long userId, UserUpdateRequestDTO request) {
        log.info("[updateUser] userId={} 정보 수정 시작", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));

        user.updateUserInfo(
                request.password(),
                request.isActive()
        );

        log.info("[updateUser] userId={} 정보 수정 완료", userId);
        return UserResponseDTO.fromEntity(user);
    }

    /**
     * 사용하지 않은 UID/GID를 찾아 할당
     */
    @Transactional
    public UnixIdResult allocateNextAvailableUidGid(String username, String groupname) {
        if (groupname == null || groupname.isBlank()) {
            groupname = username;
        }

        Long uid = getOrCreateUid(username);
        Long gid = getOrCreateGid(groupname, username, uid);

        return new UnixIdResult(uid, gid);
    }

    private Long getOrCreateUid(String username) {
        Optional<Long> existingUid = userGroupRepository.findUidByGroupName(username);
        if (existingUid.isPresent()) return existingUid.get();

        long maxUid = usedIdRepository.findMaxUid().orElse(UID_BASE - 1);
        long newUid = Math.max(UID_BASE, maxUid + 1);
        usedIdRepository.save(new UsedId(newUid));
        return newUid;
    }

    private Long getOrCreateGid(String groupname, String username, Long uid) {
        Optional<UserGroup> group = userGroupRepository.findByGroupName(groupname);
        if (group.isPresent()) {
            UserGroup existingGroup = group.get();

            boolean alreadyLinked = userGroupRepository.existsByGroupNameAndUid(groupname, uid);
            if (!alreadyLinked) {
                UsedId usedId = usedIdRepository.findById(uid)
                        .orElseThrow(() -> new IllegalStateException("UsedId not found: " + uid));

                userGroupRepository.save(UserGroup.builder()
                        .gid(existingGroup.getGid())
                        .groupName(groupname)
                        .usedId(usedId)
                        .build());
            }
            return existingGroup.getGid();
        } else {
            Long gid = groupname.equals(username) ? uid : uid + 1;
            UsedId usedId = usedIdRepository.findById(uid)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));

            userGroupRepository.save(UserGroup.builder()
                    .gid(gid)
                    .groupName(groupname)
                    .usedId(usedId)
                    .build());
            return gid;
        }
    }

    public record UnixIdResult(Long uid, Long gid) {}
}
