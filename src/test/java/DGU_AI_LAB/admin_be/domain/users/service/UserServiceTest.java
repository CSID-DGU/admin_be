package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;

import DGU_AI_LAB.admin_be.domain.users.dto.request.PasswordUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.PhoneUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserAuthRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.MyInfoResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserAuthResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .email("test@dgu.ac.kr")
                .password("encodedPassword")
                .name("홍길동")
                .studentId("2021001234")
                .phone("010-1234-5678")
                .department("컴퓨터공학과")
                .build();
    }

    @Nested
    @DisplayName("getMyInfo")
    class GetMyInfo {

        @Test
        @DisplayName("존재하는 userId로 내 정보를 조회하면 MyInfoResponseDTO를 반환한다")
        void getMyInfo_returnsDTO_whenUserExists() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            MyInfoResponseDTO result = userService.getMyInfo(1L);

            assertThat(result).isNotNull();
            assertThat(result.email()).isEqualTo("test@dgu.ac.kr");
            assertThat(result.name()).isEqualTo("홍길동");
        }

        @Test
        @DisplayName("존재하지 않는 userId로 조회하면 EntityNotFoundException을 던진다")
        void getMyInfo_throwsException_whenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getMyInfo(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getUserById")
    class GetUserById {

        @Test
        @DisplayName("존재하는 userId로 단일 유저를 조회하면 UserResponseDTO를 반환한다")
        void getUserById_returnsDTO_whenUserExists() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            UserResponseDTO result = userService.getUserById(1L);

            assertThat(result).isNotNull();
            assertThat(result.username()).isEqualTo("홍길동");
        }

        @Test
        @DisplayName("존재하지 않는 userId로 조회하면 EntityNotFoundException을 던진다")
        void getUserById_throwsException_whenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserById(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updatePassword")
    class UpdatePassword {

        @Test
        @DisplayName("현재 비밀번호가 일치하고 새 비밀번호가 다르면 비밀번호 변경에 성공한다")
        void updatePassword_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches("currentPw", "encodedPassword")).thenReturn(true);
            when(passwordEncoder.matches("newPw", "encodedPassword")).thenReturn(false);
            when(passwordEncoder.encode("newPw")).thenReturn("newEncodedPw");

            PasswordUpdateRequestDTO request = new PasswordUpdateRequestDTO("currentPw", "newPw");
            UserResponseDTO result = userService.updatePassword(1L, request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("현재 비밀번호가 틀리면 BusinessException을 던진다")
        void updatePassword_throwsException_whenCurrentPasswordWrong() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches("wrongPw", "encodedPassword")).thenReturn(false);

            PasswordUpdateRequestDTO request = new PasswordUpdateRequestDTO("wrongPw", "newPw");

            assertThatThrownBy(() -> userService.updatePassword(1L, request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 BusinessException을 던진다")
        void updatePassword_throwsException_whenNewPasswordSameAsCurrent() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches(anyString(), eq("encodedPassword"))).thenReturn(true);

            PasswordUpdateRequestDTO request = new PasswordUpdateRequestDTO("currentPw", "currentPw");

            assertThatThrownBy(() -> userService.updatePassword(1L, request))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("updatePhone")
    class UpdatePhone {

        @Test
        @DisplayName("유저가 존재하면 연락처 변경에 성공한다")
        void updatePhone_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            PhoneUpdateRequestDTO request = new PhoneUpdateRequestDTO("010-9999-8888");
            UserResponseDTO result = userService.updatePhone(1L, request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("유저가 없으면 EntityNotFoundException을 던진다")
        void updatePhone_throwsException_whenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            PhoneUpdateRequestDTO request = new PhoneUpdateRequestDTO("010-9999-8888");

            assertThatThrownBy(() -> userService.updatePhone(99L, request))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("userAuth (SSH 로그인)")
    class UserAuth {

        @Test
        @DisplayName("올바른 username과 password로 인증하면 성공 응답을 반환한다")
        void userAuth_success() {
            String passwordBase64 = "dGVzdA==";

            Request request = mock(Request.class);
            when(request.getUbuntuUsername()).thenReturn("testuser");
            when(requestRepository.findByUbuntuUsernameAndUbuntuPasswordBase64("testuser", passwordBase64))
                    .thenReturn(Optional.of(request));

            UserAuthRequestDTO dto = new UserAuthRequestDTO("testuser", passwordBase64);
            UserAuthResponseDTO result = userService.userAuth(dto);

            assertThat(result.success()).isTrue();
            assertThat(result.authenticatedUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("존재하지 않는 username으로 인증하면 UnauthorizedException을 던진다")
        void userAuth_throwsException_whenUsernameNotFound() {
            when(requestRepository.findByUbuntuUsernameAndUbuntuPasswordBase64(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            UserAuthRequestDTO dto = new UserAuthRequestDTO("unknownuser", "dGVzdA==");

            assertThatThrownBy(() -> userService.userAuth(dto))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }
}
