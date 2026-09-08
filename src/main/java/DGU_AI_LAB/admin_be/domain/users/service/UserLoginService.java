package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UserLoginRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserTokenResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import DGU_AI_LAB.admin_be.global.auth.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserLoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.refresh-token-expire-time}")
    private long REFRESH_TOKEN_EXPIRE_TIME;

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOGIN_LOCKOUT_SECONDS = 900; // 15분

    /** 회원가입 */
    @Transactional
    public void register(UserRegisterRequestDTO request) {
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

        String encoded = passwordEncoder.encode(request.password());
        User user = request.toEntity(encoded);

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
        String attemptKey = "LOGIN_FAIL:" + request.email();
        if (isLockedOut(attemptKey)) {
            throw new BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    recordFailedAttempt(attemptKey);
                    return new UnauthorizedException(ErrorCode.INVALID_LOGIN_INFO);
                });

        if (!user.getIsActive()) {
            throw new UnauthorizedException(ErrorCode.ACCOUNT_DISABLED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            recordFailedAttempt(attemptKey);
            throw new UnauthorizedException(ErrorCode.INVALID_LOGIN_INFO);
        }

        redisTemplate.delete(attemptKey);
        user.recordLogin();

        String accessToken = jwtProvider.getIssueToken(user.getUserId(), true);
        String refreshToken = jwtProvider.getIssueToken(user.getUserId(), false);

        redisTemplate.opsForValue().set(
                "RT:" + user.getUserId(), refreshToken, REFRESH_TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS
        );

        return UserTokenResponseDTO.of(accessToken, refreshToken);
    }

    /** 이메일당 15분 내 5회 실패 시 잠금 — 브루트포스 방지 */
    private boolean isLockedOut(String attemptKey) {
        String attempts = redisTemplate.opsForValue().get(attemptKey);
        return attempts != null && Integer.parseInt(attempts) >= MAX_LOGIN_ATTEMPTS;
    }

    private void recordFailedAttempt(String attemptKey) {
        Long count = redisTemplate.opsForValue().increment(attemptKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(attemptKey, LOGIN_LOCKOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

}

