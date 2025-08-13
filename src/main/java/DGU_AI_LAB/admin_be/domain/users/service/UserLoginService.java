package DGU_AI_LAB.admin_be.domain.users.service;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final GroupRepository groupRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RedisTemplate<String, String> redisTemplate;

    private final long REFRESH_TOKEN_EXPIRE_TIME = 60 * 60 * 24 * 7;

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
        log.info("회원가입 완료, VERIFIED:{} 키 삭제", request.email());
    }



    /** 로그인 */
    public UserTokenResponseDTO login(UserLoginRequestDTO request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.INVALID_LOGIN_INFO));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException(ErrorCode.INVALID_LOGIN_INFO);
        }

        String accessToken = jwtProvider.getIssueToken(user.getUserId(), true);
        String refreshToken = jwtProvider.getIssueToken(user.getUserId(), false);

        redisTemplate.opsForValue().set(
                "RT:" + user.getUserId(), refreshToken, REFRESH_TOKEN_EXPIRE_TIME, TimeUnit.SECONDS
        );

        return UserTokenResponseDTO.of(accessToken, refreshToken);
    }

}

