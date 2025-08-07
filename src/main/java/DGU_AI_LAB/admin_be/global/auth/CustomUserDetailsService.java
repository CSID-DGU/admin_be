package DGU_AI_LAB.admin_be.global.auth;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.USER_NOT_FOUND));

        return new CustomUserDetails(user, null);
    }

    /**
     * JwtAuthenticationFilter에서 user 엔티티를 직접 가져올 때 사용
     */
    public User loadUserEntityById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.USER_NOT_FOUND));
    }
}

