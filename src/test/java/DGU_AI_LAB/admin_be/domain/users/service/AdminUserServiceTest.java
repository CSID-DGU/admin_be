package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserServiceTest {

    @InjectMocks
    private AdminUserService adminUserService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private WebClient userWebClient;

    // WebClient DELETE 체이닝 mock
    @Mock
    private WebClient.RequestHeadersUriSpec<?> deleteUriSpec;
    @Mock
    private WebClient.RequestHeadersSpec<?> deleteHeadersSpec;
    @Mock
    private WebClient.ResponseSpec deleteResponseSpec;

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
    @DisplayName("getAllUsers")
    class GetAllUsers {

        @Test
        @DisplayName("유저 목록이 있으면 UserSummaryDTO 리스트를 반환한다")
        void getAllUsers_returnsUserList() {
            User user2 = User.builder()
                    .email("user2@dgu.ac.kr")
                    .password("pw")
                    .name("이순신")
                    .studentId("2021005678")
                    .phone("010-5678-1234")
                    .department("전자공학과")
                    .build();

            when(userRepository.findAll()).thenReturn(List.of(mockUser, user2));

            List<UserSummaryDTO> result = adminUserService.getAllUsers();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("유저가 없으면 빈 리스트를 반환한다")
        void getAllUsers_returnsEmptyList_whenNoUsers() {
            when(userRepository.findAll()).thenReturn(List.of());

            List<UserSummaryDTO> result = adminUserService.getAllUsers();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("유저가 존재하면 정보를 수정하고 UserResponseDTO를 반환한다")
        void updateUser_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            UserUpdateRequestDTO request = new UserUpdateRequestDTO("newPw", false);
            UserResponseDTO result = adminUserService.updateUser(1L, request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("유저가 없으면 EntityNotFoundException을 던진다")
        void updateUser_throwsException_whenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            UserUpdateRequestDTO request = new UserUpdateRequestDTO("newPw", false);

            assertThatThrownBy(() -> adminUserService.updateUser(99L, request))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("연결된 Request가 없는 유저를 삭제하면 isActive를 false로 변경한다")
        void deleteUser_withNoRequests_softDeletes() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of());

            adminUserService.deleteUser(1L);

            assertThat(mockUser.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 유저를 삭제하려 하면 EntityNotFoundException을 던진다")
        void deleteUser_throwsException_whenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.deleteUser(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteUbuntuAccount")
    class DeleteUbuntuAccount {

        @SuppressWarnings("unchecked")
        private void stubDeleteSuccess() {
            when(userWebClient.delete()).thenReturn((WebClient.RequestHeadersUriSpec) deleteUriSpec);
            when(deleteUriSpec.uri(anyString(), anyString())).thenReturn((WebClient.RequestHeadersSpec) deleteHeadersSpec);
            when(deleteHeadersSpec.retrieve()).thenReturn(deleteResponseSpec);
            when(deleteResponseSpec.onStatus(any(), any())).thenReturn(deleteResponseSpec);
            when(deleteResponseSpec.toBodilessEntity()).thenReturn(Mono.empty());
        }

        @Test
        @DisplayName("DELETE /accounts/users/{username}으로 요청을 전송한다")
        void deleteUbuntuAccount_sendsDELETERequest() {
            Request mockRequest = mock(Request.class);
            when(requestRepository.findByUbuntuUsername("testuser")).thenReturn(Optional.of(mockRequest));
            stubDeleteSuccess();

            adminUserService.deleteUbuntuAccount("testuser");

            verify(userWebClient).delete();
            verify(deleteUriSpec).uri("/accounts/users/{username}", "testuser");
        }

        @Test
        @DisplayName("config-server가 400을 응답하면 INVALID_USERNAME_FORMAT BusinessException을 던진다")
        void deleteUbuntuAccount_throws_whenBadRequest() {
            Request mockRequest = mock(Request.class);
            when(requestRepository.findByUbuntuUsername("baduser")).thenReturn(Optional.of(mockRequest));

            when(userWebClient.delete()).thenReturn((WebClient.RequestHeadersUriSpec) deleteUriSpec);
            when(deleteUriSpec.uri(anyString(), anyString())).thenReturn((WebClient.RequestHeadersSpec) deleteHeadersSpec);
            when(deleteHeadersSpec.retrieve()).thenReturn(deleteResponseSpec);
            when(deleteResponseSpec.onStatus(any(), any())).thenAnswer(inv -> {
                java.util.function.Predicate<org.springframework.http.HttpStatusCode> predicate = inv.getArgument(0);
                if (predicate.test(HttpStatus.BAD_REQUEST)) {
                    org.springframework.web.reactive.function.client.ClientResponse clientResponse =
                            mock(org.springframework.web.reactive.function.client.ClientResponse.class);
                    when(inv.<java.util.function.Function<org.springframework.web.reactive.function.client.ClientResponse, reactor.core.publisher.Mono<? extends Throwable>>>getArgument(1)
                            .apply(clientResponse))
                            .thenReturn(Mono.error(new BusinessException(DGU_AI_LAB.admin_be.error.ErrorCode.INVALID_USERNAME_FORMAT)));
                }
                return deleteResponseSpec;
            });
            when(deleteResponseSpec.toBodilessEntity())
                    .thenReturn(Mono.error(new BusinessException(DGU_AI_LAB.admin_be.error.ErrorCode.INVALID_USERNAME_FORMAT)));

            assertThatThrownBy(() -> adminUserService.deleteUbuntuAccount("baduser"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Request가 없으면 EntityNotFoundException을 던진다")
        void deleteUbuntuAccount_throws_whenRequestNotFound() {
            when(requestRepository.findByUbuntuUsername("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.deleteUbuntuAccount("unknown"))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }
}
