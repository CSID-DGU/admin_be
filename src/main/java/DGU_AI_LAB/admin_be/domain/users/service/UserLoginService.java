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

        String encoded = passwordEncoder.encode(request.password());
        User user = request.toEntity(encoded);

        userRepository.save(user);

        redisTemplate.delete(redisKey);
        log.info("회원가입 완료 및 이메일 인증 키 삭제");
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

