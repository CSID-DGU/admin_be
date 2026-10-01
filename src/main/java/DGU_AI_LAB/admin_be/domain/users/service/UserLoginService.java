package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.global.auth.EmailDomainPolicy;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserLoginRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserTokenResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import DGU_AI_LAB.admin_be.global.auth.jwt.JwtProvider;
import DGU_AI_LAB.admin_be.global.validation.ReservedLinuxNames;
import DGU_AI_LAB.admin_be.global.util.LinuxPasswordHasher;
import DGU_AI_LAB.admin_be.global.util.RedisWindowCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserLoginService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final ReservedLinuxNames reservedLinuxNames;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final EmailDomainPolicy emailDomainPolicy;
    private final RedisWindowCounter windowCounter;

    @Value("${jwt.refresh-token-expire-time}")
    private long REFRESH_TOKEN_EXPIRE_TIME;

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_LOCKOUT_WINDOW = Duration.ofMinutes(15);

    /**
     * 없는 이메일에도 비밀번호 검사를 한 번 돌리기 위한 해시. 건너뛰면 응답이 BCrypt 한 번만큼 빨라져 가입된
     * 이메일인지 응답 시간으로 드러난다(Spring Security DaoAuthenticationProvider와 같은 방식).
     */
    private volatile String userNotFoundEncodedPassword;

    /** 회원가입 */
    @Transactional
    public void register(UserRegisterRequestDTO request) {
        emailDomainPolicy.requireAllowed(request.email());
        String redisKey = "VERIFIED:" + request.email();

        if (!Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            throw new UnauthorizedException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        // 우분투 유저네임은 가입 시 한 번 정해져 이 웹 계정에 평생 귀속된다 — 홈 디렉터리가
        // 유저네임으로만 결정되므로, 유일성 검사도 신청이 아니라 여기서 해야 한다.
        if (userRepository.existsByUbuntuUsername(request.ubuntuUsername())) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }
        if (reservedLinuxNames.contains(request.ubuntuUsername())) {
            throw new BusinessException(ErrorCode.UBUNTU_USERNAME_RESERVED);
        }
        // 개인 그룹도 계정명으로 만들고 AD는 사용자·그룹 이름 공간을 공유한다 — 같은 이름의 그룹이
        // 있으면 승인 뒤 계정 생성이 실패한다.
        if (groupRepository.existsByGroupName(request.ubuntuUsername())) {
            throw new BusinessException(ErrorCode.UBUNTU_USERNAME_CONFLICTS_GROUP);
        }

        String encoded = passwordEncoder.encode(request.password());
        User user = request.toEntity(encoded);
        // 웹 계정 비밀번호가 곧 SSH(Ubuntu) 비밀번호다. 평문은 지금만 있으므로 리눅스용 해시를 함께 만든다.
        user.changeUbuntuPasswordHash(LinuxPasswordHasher.sha512Crypt(request.password()));

        try {
            // 위 사전 검사와 여기 사이에 같은 이메일/유저네임으로 동시에 가입이 들어올 수 있다.
            // 실제 방어선은 unique 제약이므로, 그 위반을 잡아 사전 검사와 같은 409로 변환한다
            // (안 잡으면 그대로 500으로 새어나간다).
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw duplicateSignupException(e);
        }

        redisTemplate.delete(redisKey);
        log.info("회원가입 완료 및 이메일 인증 키 삭제");
    }



    /**
     * 가입 시 unique 제약 위반이 이메일 때문인지 우분투 유저네임 때문인지 구분한다.
     * 둘 다 409지만 안내 문구가 달라서, 사용자가 어느 값을 고쳐야 하는지 알려면 나눠야 한다.
     * 저장이 실패한 트랜잭션에서는 재조회로 확인할 수 없으므로(rollback-only) 제약 이름으로 판별하고,
     * 판별에 실패하면 기존 동작대로 이메일 중복으로 처리한다.
     */
    private BusinessException duplicateSignupException(DataIntegrityViolationException e) {
        String cause = e.getMostSpecificCause().getMessage();
        if (cause != null && cause.toLowerCase().contains("ubuntu_username")) {
            log.warn("[register] 우분투 유저네임 중복으로 가입 실패 (uk_users_ubuntu_username 위반)");
            return new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }
        log.warn("[register] 이메일 중복으로 가입 실패 (email unique 제약 위반)");
        return new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
    }

    /** 로그인 */
    public UserTokenResponseDTO login(UserLoginRequestDTO request) {
        // DB는 이메일 대소문자를 구분하지 않아(utf8mb4_0900_ai_ci) 표기만 바꿔도 같은 계정으로 로그인된다.
        // 잠금 키도 같은 기준으로 세야 대소문자 조합마다 5회씩 새로 받아 잠금을 피하지 못한다.
        String attemptKey = loginAttemptKey(request.email());
        if (isLockedOut(attemptKey)) {
            throw new BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }

        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), userNotFoundEncodedPassword());
            recordFailedAttempt(attemptKey);
            throw new UnauthorizedException(ErrorCode.INVALID_LOGIN_INFO);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            recordFailedAttempt(attemptKey);
            throw new UnauthorizedException(ErrorCode.INVALID_LOGIN_INFO);
        }

        // 비활성 여부는 비밀번호가 맞은 뒤에만 알린다. 먼저 알리면 아무 비밀번호로도 가입 여부가 드러난다.
        if (!user.getIsActive()) {
            throw new UnauthorizedException(ErrorCode.ACCOUNT_DISABLED);
        }

        redisTemplate.delete(attemptKey);
        userRepository.recordLogin(user.getUserId(), LocalDateTime.now());
        refreshSshPasswordHashIfWeak(user, request.password());

        String accessToken = jwtProvider.getIssueToken(user.getUserId(), true);
        String refreshToken = jwtProvider.getIssueToken(user.getUserId(), false);

        redisTemplate.opsForValue().set(
                "RT:" + user.getUserId(), refreshToken, REFRESH_TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS
        );

        return UserTokenResponseDTO.of(accessToken, refreshToken);
    }

    /**
     * SSH 비밀번호가 웹 비밀번호로 합쳐지기 전에 가입한 계정은 리눅스용 해시가 없고, 반복 횟수를 올리기 전에
     * 만든 해시는 약하다. 평문을 볼 수 있는 로그인 때 지금 강도로 다시 만들어, 다음 컨테이너부터 이 해시를 쓴다.
     * 이미 떠 있는 컨테이너는 건드리지 않는다(로그인이 config-server를 기다리지 않게) — 비밀번호가 같으니
     * 접속은 그대로 되고, 웹 비밀번호를 바꾸면 그때 함께 바뀐다.
     */
    private void refreshSshPasswordHashIfWeak(User user, String rawPassword) {
        if (LinuxPasswordHasher.isCurrentStrength(user.getUbuntuPasswordHash())) {
            return;
        }
        String hash = LinuxPasswordHasher.sha512Crypt(rawPassword);
        if (userRepository.replaceWeakUbuntuPasswordHash(user.getUserId(), hash, LinuxPasswordHasher.currentPrefix()) > 0) {
            log.info("[login] userId={} SSH 비밀번호 해시를 지금 강도로 다시 만듦", user.getUserId());
        }
    }

    private String userNotFoundEncodedPassword() {
        String encoded = userNotFoundEncodedPassword;
        if (encoded == null) {
            encoded = passwordEncoder.encode("userNotFoundPassword");
            userNotFoundEncodedPassword = encoded;
        }
        return encoded;
    }

    /** 메일 인증으로 본인이 확인돼 비밀번호를 새로 정했으면, 그 전에 쌓인 실패 횟수로 계속 막지 않는다. */
    public void clearFailedAttempts(String email) {
        redisTemplate.delete(loginAttemptKey(email));
    }

    private static String loginAttemptKey(String email) {
        return "LOGIN_FAIL:" + email.trim().toLowerCase(Locale.ROOT);
    }

    /** 이메일당 15분 내 5회 실패 시 잠금 — 브루트포스 방지 */
    private boolean isLockedOut(String attemptKey) {
        String attempts = redisTemplate.opsForValue().get(attemptKey);
        return attempts != null && Integer.parseInt(attempts) >= MAX_LOGIN_ATTEMPTS;
    }

    private void recordFailedAttempt(String attemptKey) {
        windowCounter.increment(attemptKey, LOGIN_LOCKOUT_WINDOW);
    }

}

