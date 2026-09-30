package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.global.util.LinuxPasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자가 사용자 비밀번호를 새로 지정한다. 사용자가 직접 바꾸는 기능은 두지 않으므로, 비밀번호가 새거나 잊었을 때
 * 쓰는 유일한 경로다. 웹 비밀번호가 곧 SSH(Ubuntu) 비밀번호라 둘을 함께 바꾼다.
 *
 * <p>사용자 행을 잠근 채 config-server에 먼저 반영하고(리눅스 계정이 있을 때 — 떠 있는 컨테이너 포함), 성공해야 DB의 두
 * 해시를 바꾼다. 반영이 실패하면 롤백돼 둘이 어긋나지 않는다 — 컨테이너 일부가 이미 바뀌었어도 다시 요청하면 맞춰진다.
 * 관리자만 부르므로 같은 사용자에 대한 요청이 겹칠 일이 드물어 잠금을 기다리게 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPasswordResetService {

    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    private final PasswordEncoder passwordEncoder;
    private final UbuntuPasswordSyncClient ubuntuPasswordSyncClient;
    private final TokenService tokenService;

    @Transactional
    public UserSummaryDTO resetPassword(Long userId, String newPassword) {
        log.info("[resetPassword] userId={} 관리자 비밀번호 초기화 시도", userId);

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));

        // 생성 작업은 승인 때 읽은 해시로 계정을 만든다. 그 사이 바꾸면 DB만 새 해시가 되고 실제 계정은
        // 옛 비밀번호로 남는다. 승인도 이 사용자 행을 잠그고 해시를 읽으므로 이 확인과 겹치지 않는다.
        if (requestRepository.existsByUser_UserIdAndStatus(userId, Status.PROCESSING)) {
            throw new BusinessException(ErrorCode.UBUNTU_PASSWORD_CHANGE_WHILE_PROVISIONING);
        }

        String sshPasswordHash = LinuxPasswordHasher.sha512Crypt(newPassword);
        // 리눅스 계정이 아직 없으면(첫 승인 전, 또는 회수 뒤) 바꿀 컨테이너도 없다. 해시만 두면 다음 승인이
        // 이 값으로 계정을 만든다.
        if (user.hasUbuntuAccount()) {
            ubuntuPasswordSyncClient.apply(user.getUbuntuUsername(), sshPasswordHash);
        }
        user.updatePassword(passwordEncoder.encode(newPassword));
        user.changeUbuntuPasswordHash(sshPasswordHash);
        // 새거나 잊은 비밀번호로 이미 들어와 있는 세션을 끊는다(리프레시 토큰 삭제).
        tokenService.logout(userId);
        log.info("[resetPassword] userId={} 관리자 비밀번호 초기화 완료(SSH 포함)", userId);
        return UserSummaryDTO.fromEntity(user);
    }
}
