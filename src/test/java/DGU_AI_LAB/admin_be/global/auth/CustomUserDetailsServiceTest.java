package DGU_AI_LAB.admin_be.global.auth;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService")
class CustomUserDetailsServiceTest {

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private UserRepository userRepository;

    private User activeUser;
    private User inactiveUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .email("active@dgu.ac.kr")
                .password("encoded")
                .name("활성유저")
                .studentId("2021001111")
                .phone("010-1111-1111")
                .department("컴퓨터공학과")
                .build();

        inactiveUser = User.builder()
                .email("inactive@dgu.ac.kr")
                .password("encoded")
                .name("비활성유저")
                .studentId("2021002222")
                .phone("010-2222-2222")
                .department("컴퓨터공학과")
                .build();
        inactiveUser.withdraw(); // isActive = false, deletedAt 설정
    }

    @Nested
    @DisplayName("loadUserEntityById")
    class LoadUserEntityById {

        @Test
        @DisplayName("활성 계정은 정상 반환된다")
        void loadUserEntityById_returnsUser_whenActive() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

            User result = customUserDetailsService.loadUserEntityById(1L);

            assertThat(result).isEqualTo(activeUser);
        }

        @Test
        @DisplayName("존재하지 않는 userId면 UnauthorizedException을 던진다")
        void loadUserEntityById_throwsUnauthorized_whenUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customUserDetailsService.loadUserEntityById(999L))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("soft-delete된 계정(isActive=false)은 UnauthorizedException을 던진다 — JWT 토큰이 유효해도 차단")
        void loadUserEntityById_throwsUnauthorized_whenAccountInactive() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(inactiveUser));

            assertThatThrownBy(() -> customUserDetailsService.loadUserEntityById(2L))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("관리자가 사용자를 삭제한 직후 해당 userId로 조회하면 UnauthorizedException이 발생한다")
        void loadUserEntityById_throwsUnauthorized_afterAdminDeletesUser() {
            // 관리자가 deleteUser() 호출 → user.withdraw() → isActive=false
            when(userRepository.findById(3L)).thenReturn(Optional.of(inactiveUser));

            assertThatThrownBy(() -> customUserDetailsService.loadUserEntityById(3L))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }
}
