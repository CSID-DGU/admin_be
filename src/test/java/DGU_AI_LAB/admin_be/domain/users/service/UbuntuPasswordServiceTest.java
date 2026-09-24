package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UbuntuPasswordUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UbuntuPasswordServiceTest {

    private static final String OLD_HASH = "$6$oldsalt$oldhash";

    @InjectMocks
    private UbuntuPasswordService service;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentPasswordVerifier currentPasswordVerifier;

    @Mock
    private UbuntuPasswordSyncClient syncClient;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("test@dgu.ac.kr").password("encodedWebPassword").name("홍길동")
                .studentId("2021001234").phone("010-0000-0000").department("컴퓨터공학과")
                .ubuntuUsername("honggildong")
                .build();
        user.changeUbuntuPasswordHash(OLD_HASH);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
    }

    private static UbuntuPasswordUpdateRequestDTO request() {
        return new UbuntuPasswordUpdateRequestDTO("webPassword", "newUbuntuPw1!");
    }

    @Test
    @DisplayName("리눅스 계정이 있으면 컨테이너에 먼저 반영한 뒤 계정 해시를 바꾼다")
    void appliesToContainersThenStoresHash() {
        user.assignUbuntuAccount(50001L, 50001L);

        assertThat(service.changeUbuntuPassword(1L, request()).hasUbuntuPassword()).isTrue();

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(syncClient).apply(eq("honggildong"), hash.capture());
        assertThat(hash.getValue()).startsWith("$6$").doesNotContain("newUbuntuPw1!");
        assertThat(user.getUbuntuPasswordHash()).isEqualTo(hash.getValue());
    }

    @Test
    @DisplayName("리눅스 계정이 아직 없으면 config-server를 부르지 않고 해시만 저장한다")
    void storesHashOnlyWithoutAccount() {

        service.changeUbuntuPassword(1L, request());

        verify(syncClient, never()).apply(anyString(), anyString());
        assertThat(user.getUbuntuPasswordHash()).startsWith("$6$").isNotEqualTo(OLD_HASH);
    }

    @Test
    @DisplayName("웹 비밀번호가 틀리면 INVALID_PASSWORD, 아무것도 바꾸지 않는다")
    void rejectsWrongWebPassword() {
        user.assignUbuntuAccount(50001L, 50001L);
        doThrow(new BusinessException(ErrorCode.INVALID_PASSWORD))
                .when(currentPasswordVerifier).verify(user, "webPassword");

        assertThatThrownBy(() -> service.changeUbuntuPassword(1L, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PASSWORD);
        verify(syncClient, never()).apply(anyString(), anyString());
        assertThat(user.getUbuntuPasswordHash()).isEqualTo(OLD_HASH);
    }

    @Test
    @DisplayName("컨테이너 반영이 실패하면 계정 해시는 그대로 둔다")
    void keepsHashWhenSyncFails() {
        user.assignUbuntuAccount(50001L, 50001L);
        doThrow(new BusinessException(ErrorCode.UBUNTU_PASSWORD_CHANGE_FAILED))
                .when(syncClient).apply(eq("honggildong"), any());

        assertThatThrownBy(() -> service.changeUbuntuPassword(1L, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UBUNTU_PASSWORD_CHANGE_FAILED);
        assertThat(user.getUbuntuPasswordHash()).isEqualTo(OLD_HASH);
    }
}
