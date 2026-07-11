package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PodService")
class PodServiceTest {

    @InjectMocks
    private PodService podService;

    @Mock private WebClient webClient;
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    }

    // ───────────────────────────────────────────────────────────────
    // deletePod
    // ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("deletePod")
    class DeletePod {

        @Test
        @DisplayName("podName이 null이면 API를 호출하지 않고 정상 반환한다")
        void deletePod_skipsApiCall_whenPodNameIsNull() {
            assertThatCode(() -> podService.deletePod(null))
                    .doesNotThrowAnyException();

            verify(webClient, never()).post();
        }

        @Test
        @DisplayName("정상 응답이면 Pod 삭제에 성공한다")
        void deletePod_success_whenApiReturnsOk() {
            when(responseSpec.bodyToMono(Map.class))
                    .thenReturn(Mono.just(Map.of("status", "ok")));

            assertThatCode(() -> podService.deletePod("test-pod-name"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("API 호출 중 BusinessException이 발생하면 그대로 전파한다")
        void deletePod_propagatesBusinessException() {
            when(responseSpec.bodyToMono(Map.class))
                    .thenReturn(Mono.error(new BusinessException("Pod 삭제 실패", ErrorCode.POD_DELETION_FAILED)));

            assertThatThrownBy(() -> podService.deletePod("error-pod"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Pod 삭제 실패");
        }

        @Test
        @DisplayName("API 호출 중 일반 예외가 발생하면 BusinessException으로 래핑한다")
        void deletePod_wrapsGeneralException_asBusinessException() {
            when(responseSpec.bodyToMono(Map.class))
                    .thenReturn(Mono.error(new RuntimeException("connection timeout")));

            assertThatThrownBy(() -> podService.deletePod("timeout-pod"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("deletePod 메서드에 @Transactional 어노테이션이 없다")
        void deletePod_hasNoTransactionalAnnotation() throws NoSuchMethodException {
            var method = PodService.class.getMethod("deletePod", String.class);
            var txAnnotation = method.getAnnotation(Transactional.class);

            assertThat(txAnnotation)
                    .as("Propagation.MANDATORY 등 트랜잭션 어노테이션이 제거되어야 함")
                    .isNull();
        }
    }

    // ───────────────────────────────────────────────────────────────
    // createPod
    // ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("createPod")
    class CreatePod {

        @Test
        @DisplayName("Pod 생성 API가 성공 응답을 반환하면 CreatePodResponseDTO를 반환한다")
        void createPod_returnsDto_whenApiSucceeds() {
            CreatePodResponseDTO mockResponse = new CreatePodResponseDTO(
                    "running", "node-01", "pod-testuser-abc",
                    List.of(new CreatePodResponseDTO.PortInfo("ssh", 22, 30022))
            );
            when(responseSpec.bodyToMono(CreatePodResponseDTO.class))
                    .thenReturn(Mono.just(mockResponse));

            CreatePodResponseDTO result = podService.createPod("testuser");

            assertThat(result).isEqualTo(mockResponse);
            assertThat(result.podName()).isEqualTo("pod-testuser-abc");
        }

        @Test
        @DisplayName("C-3: API가 빈 응답(null)을 반환하면 POD_CREATION_FAILED 예외가 발생한다")
        void createPod_throwsBusinessException_whenApiReturnsEmpty() {
            when(responseSpec.bodyToMono(CreatePodResponseDTO.class))
                    .thenReturn(Mono.empty());

            assertThatThrownBy(() -> podService.createPod("testuser"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POD_CREATION_FAILED);
        }

        @Test
        @DisplayName("C-3: podName이 null인 응답이면 POD_CREATION_FAILED 예외가 발생한다")
        void createPod_throwsBusinessException_whenPodNameIsNull() {
            CreatePodResponseDTO badResponse = new CreatePodResponseDTO(
                    "unknown", "farm1", null, List.of()
            );
            when(responseSpec.bodyToMono(CreatePodResponseDTO.class))
                    .thenReturn(Mono.just(badResponse));

            assertThatThrownBy(() -> podService.createPod("testuser"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POD_CREATION_FAILED);
        }

        @Test
        @DisplayName("Pod 생성 API 호출 중 BusinessException이 발생하면 그대로 전파한다")
        void createPod_propagatesBusinessException() {
            when(responseSpec.bodyToMono(CreatePodResponseDTO.class))
                    .thenReturn(Mono.error(new BusinessException("Pod 생성 실패", ErrorCode.POD_CREATION_FAILED)));

            assertThatThrownBy(() -> podService.createPod("testuser"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Pod 생성 실패");
        }

        @Test
        @DisplayName("Pod 생성 API 호출 중 일반 예외가 발생하면 BusinessException으로 래핑한다")
        void createPod_wrapsGeneralException_asBusinessException() {
            when(responseSpec.bodyToMono(CreatePodResponseDTO.class))
                    .thenReturn(Mono.error(new RuntimeException("network error")));

            assertThatThrownBy(() -> podService.createPod("testuser"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("올바른 username으로 /create-pod URI에 요청한다")
        void createPod_callsCorrectUri() {
            when(responseSpec.bodyToMono(CreatePodResponseDTO.class))
                    .thenReturn(Mono.just(new CreatePodResponseDTO("running", "node", "pod-user", List.of())));

            podService.createPod("myuser");

            verify(requestBodyUriSpec).uri("/create-pod");
        }
    }
}
