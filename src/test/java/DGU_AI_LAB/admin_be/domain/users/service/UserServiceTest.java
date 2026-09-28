package DGU_AI_LAB.admin_be.domain.users.service;

import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;

import DGU_AI_LAB.admin_be.domain.users.dto.request.PasswordUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.PhoneUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UbuntuUsernameRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.MyInfoResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.global.validation.ReservedLinuxNames;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
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
    private ReservedLinuxNames reservedLinuxNames;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CurrentPasswordVerifier currentPasswordVerifier;

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private UbuntuPasswordSyncClient ubuntuPasswordSyncClient;

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
    @DisplayName("updatePassword — 웹 비밀번호가 곧 SSH 비밀번호")
    class UpdatePassword {

        @BeforeEach
        void lockUser() {
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockUser));
        }

        @Test
        @DisplayName("리눅스 계정이 없으면 컨테이너 반영 없이 웹 비밀번호와 SSH 해시를 함께 바꾼다")
        void updatePassword_withoutAccount_updatesBothHashes() {
            when(passwordEncoder.matches("newPassword1!", "encodedPassword")).thenReturn(false);
            when(passwordEncoder.encode("newPassword1!")).thenReturn("newEncodedPw");

            UserResponseDTO result = userService.updatePassword(1L, new PasswordUpdateRequestDTO("currentPw", "newPassword1!"));

            assertThat(result).isNotNull();
            assertThat(mockUser.getPassword()).isEqualTo("newEncodedPw");
            assertThat(mockUser.getUbuntuPasswordHash()).startsWith("$6$").doesNotContain("newPassword1!");
            verifyNoInteractions(ubuntuPasswordSyncClient);
        }

        @Test
        @DisplayName("리눅스 계정이 있으면 컨테이너에 먼저 반영하고 같은 해시를 저장한다")
        void updatePassword_withAccount_appliesToContainersFirst() {
            mockUser.assignUbuntuAccount(21000L, 21000L);
            ReflectionTestUtils.setField(mockUser, "ubuntuUsername", "honggildong");
            when(passwordEncoder.matches("newPassword1!", "encodedPassword")).thenReturn(false);
            when(passwordEncoder.encode("newPassword1!")).thenReturn("newEncodedPw");

            userService.updatePassword(1L, new PasswordUpdateRequestDTO("currentPw", "newPassword1!"));

            ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
            verify(ubuntuPasswordSyncClient).apply(eq("honggildong"), hash.capture());
            assertThat(mockUser.getUbuntuPasswordHash()).isEqualTo(hash.getValue());
            assertThat(mockUser.getPassword()).isEqualTo("newEncodedPw");
        }

        @Test
        @DisplayName("컨테이너 반영이 실패하면 웹 비밀번호도 SSH 해시도 바꾸지 않는다")
        void updatePassword_syncFailure_changesNothing() {
            mockUser.assignUbuntuAccount(21000L, 21000L);
            ReflectionTestUtils.setField(mockUser, "ubuntuUsername", "honggildong");
            mockUser.changeUbuntuPasswordHash("$6$old$hash");
            when(passwordEncoder.matches("newPassword1!", "encodedPassword")).thenReturn(false);
            doThrow(new BusinessException(ErrorCode.UBUNTU_PASSWORD_CHANGE_FAILED))
                    .when(ubuntuPasswordSyncClient).apply(eq("honggildong"), anyString());

            assertThatThrownBy(() -> userService.updatePassword(1L, new PasswordUpdateRequestDTO("currentPw", "newPassword1!")))
                    .isInstanceOf(BusinessException.class);
            assertThat(mockUser.getPassword()).isEqualTo("encodedPassword");
            assertThat(mockUser.getUbuntuPasswordHash()).isEqualTo("$6$old$hash");
        }

        @Test
        @DisplayName("컨테이너 생성 중이면 409로 막고 아무것도 바꾸지 않는다")
        void updatePassword_whileProvisioning_isRejected() {
            when(passwordEncoder.matches("newPassword1!", "encodedPassword")).thenReturn(false);
            when(requestRepository.existsByUser_UserIdAndStatus(1L, Status.PROCESSING)).thenReturn(true);

            assertThatThrownBy(() -> userService.updatePassword(1L, new PasswordUpdateRequestDTO("currentPw", "newPassword1!")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UBUNTU_PASSWORD_CHANGE_WHILE_PROVISIONING);
            assertThat(mockUser.getPassword()).isEqualTo("encodedPassword");
            verifyNoInteractions(ubuntuPasswordSyncClient);
        }

        @Test
        @DisplayName("현재 비밀번호가 틀리면 BusinessException을 던진다")
        void updatePassword_throwsException_whenCurrentPasswordWrong() {
            doThrow(new BusinessException(ErrorCode.INVALID_PASSWORD))
                    .when(currentPasswordVerifier).verify(mockUser, "wrongPw");

            assertThatThrownBy(() -> userService.updatePassword(1L, new PasswordUpdateRequestDTO("wrongPw", "newPw")))
                    .isInstanceOf(BusinessException.class);
            verifyNoInteractions(ubuntuPasswordSyncClient);
        }

        @Test
        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 BusinessException을 던진다")
        void updatePassword_throwsException_whenNewPasswordSameAsCurrent() {
            when(passwordEncoder.matches(anyString(), eq("encodedPassword"))).thenReturn(true);

            assertThatThrownBy(() -> userService.updatePassword(1L, new PasswordUpdateRequestDTO("currentPw", "currentPw")))
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
    @DisplayName("registerUbuntuUsername")
    class RegisterUbuntuUsername {

        @Test
        @DisplayName("시스템 예약 이름이면 UBUNTU_USERNAME_RESERVED를 던지고 저장하지 않는다")
        void registerUbuntuUsername_throwsException_whenReserved() {
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockUser));
            when(reservedLinuxNames.contains("docker")).thenReturn(true);

            assertThatThrownBy(() -> userService.registerUbuntuUsername(1L, new UbuntuUsernameRegisterRequestDTO("docker")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UBUNTU_USERNAME_RESERVED);
            assertThat(mockUser.getUbuntuUsername()).isNull();
        }

        @Test
        @DisplayName("같은 이름의 그룹이 있으면 UBUNTU_USERNAME_CONFLICTS_GROUP을 던지고 저장하지 않는다")
        void registerUbuntuUsername_throwsException_whenGroupNameExists() {
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockUser));
            when(userRepository.existsByUbuntuUsername("developers")).thenReturn(false);
            when(groupRepository.existsByGroupName("developers")).thenReturn(true);

            assertThatThrownBy(() -> userService.registerUbuntuUsername(1L, new UbuntuUsernameRegisterRequestDTO("developers")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UBUNTU_USERNAME_CONFLICTS_GROUP);
            verify(userRepository, never()).saveAndFlush(any());
        }
    }
}
