package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
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
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UbuntuAccountService")
class UbuntuAccountServiceTest {

    @InjectMocks
    private UbuntuAccountService ubuntuAccountService;

    @Mock private WebClient webClient;
    @Mock private WebClient.RequestHeadersUriSpec deleteUriSpec;
    @Mock private WebClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(webClient.delete()).thenReturn(deleteUriSpec);
        when(deleteUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    }

    @Nested
    @DisplayName("deleteUbuntuAccount")
    class DeleteUbuntuAccount {

        @Test
        @DisplayName("정상 응답이면 계정 삭제에 성공한다")
        void deleteUbuntuAccount_success_whenApiReturnsOk() {
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of("status", "deleted")));

            assertThatCode(() -> ubuntuAccountService.deleteUbuntuAccount("testuser"))
                    .doesNotThrowAnyException();

            verify(deleteUriSpec).uri("/accounts/users/testuser");
        }

        @Test
        @DisplayName("반응형 체인 내부에서 BusinessException이 발생해도 바깥 catch(Exception)에 잡혀 INTERNAL_SERVER_ERROR로 재래핑된다")
        void deleteUbuntuAccount_wrapsInnerBusinessException_asInternalServerError() {
            // UbuntuAccountService는 PodService와 달리 catch(BusinessException e){throw e;} 분기가 없어서,
            // onStatus 매퍼가 만든 BusinessException도 바깥 catch(Exception)에 그대로 잡혀 재래핑된다 (기존 동작 그대로).
            when(responseSpec.bodyToMono(Map.class))
                    .thenReturn(Mono.error(new BusinessException("사용자 삭제 실패", ErrorCode.UBUNTU_USER_DELETION_FAILED)));

            assertThatThrownBy(() -> ubuntuAccountService.deleteUbuntuAccount("testuser"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("API 호출 중 일반 예외가 발생하면 BusinessException으로 래핑한다")
        void deleteUbuntuAccount_wrapsGeneralException_asBusinessException() {
            when(responseSpec.bodyToMono(Map.class))
                    .thenReturn(Mono.error(new RuntimeException("connection timeout")));

            assertThatThrownBy(() -> ubuntuAccountService.deleteUbuntuAccount("testuser"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
