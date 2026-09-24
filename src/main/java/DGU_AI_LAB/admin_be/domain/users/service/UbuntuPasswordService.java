package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UbuntuPasswordUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.MyInfoResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.util.LinuxPasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 단위 Ubuntu 비밀번호 변경. 웹 계정의 해시를 바꾸고, 리눅스 계정이 이미 있으면 떠 있는
 * 컨테이너까지 함께 바꾼다 — 다음에 만들어지는 컨테이너는 웹 계정의 해시로 만들어진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UbuntuPasswordService {

    private final UserRepository userRepository;
    private final CurrentPasswordVerifier currentPasswordVerifier;
    private final UbuntuPasswordSyncClient ubuntuPasswordSyncClient;

    /**
     * 사용자 행을 잠근 채 config-server를 부른다. 같은 사용자의 변경이 겹쳐도 컨테이너와 DB가 같은
     * 순서로 바뀌게 하려는 것이다(잠금 범위는 그 사용자 한 명이다). config-server가 실패하면 예외로
     * 롤백돼 DB는 옛 해시를 그대로 가진다 — 컨테이너 일부가 이미 바뀌었어도 다시 요청하면 맞춰진다.
     */
    @Transactional
    public MyInfoResponseDTO changeUbuntuPassword(Long userId, UbuntuPasswordUpdateRequestDTO request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        currentPasswordVerifier.verify(user, request.currentPassword());

        String passwordHash = LinuxPasswordHasher.sha512Crypt(request.newPassword());
        // 리눅스 계정이 아직 없으면(첫 승인 전, 또는 회수 뒤) 바꿀 컨테이너도 없다. 해시만 두면
        // 다음 승인이 이 값으로 계정을 만든다.
        if (user.hasUbuntuAccount()) {
            ubuntuPasswordSyncClient.apply(user.getUbuntuUsername(), passwordHash);
        }
        user.changeUbuntuPasswordHash(passwordHash);
        log.info("[changeUbuntuPassword] userId={} Ubuntu 비밀번호 변경 완료", userId);
        return MyInfoResponseDTO.fromEntity(user);
    }
}
