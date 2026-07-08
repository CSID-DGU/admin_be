package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.CreatePodRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PodServiceTest {

    @Mock private WebClient webClient;
    @Mock private WebClient.RequestBodyUriSpec postUriSpec;
    @Mock private WebClient.RequestBodySpec postBodySpec;
    @Mock private WebClient.RequestHeadersSpec<?> postHeadersSpec;
    @Mock private WebClient.ResponseSpec postResponseSpec;

    private PodService podService;

    @BeforeEach
    void setUp() {
        podService = new PodService(webClient);
    }

    @SuppressWarnings("unchecked")
    private void stubWebClientPost(Mono<?> responseMono) {
        when(webClient.post()).thenReturn(postUriSpec);
        when(postUriSpec.uri(anyString())).thenReturn(postBodySpec);
        doReturn(postHeadersSpec).when(postBodySpec).bodyValue(any());
        when(postHeadersSpec.retrieve()).thenReturn(postResponseSpec);
        when(postResponseSpec.onStatus(any(), any())).thenReturn(postResponseSpec);
        doReturn(responseMono).when(postResponseSpec).bodyToMono(CreatePodResponseDTO.class);
    }

    @Nested
    @DisplayName("createPod 정상 케이스")
    class CreatePodSuccess {

        @Test
        @DisplayName("정상 응답이면 CreatePodResponseDTO를 반환한다")
        void createPod_validResponse_returnsDto() {
            // Given
            CreatePodResponseDTO expected = new CreatePodResponseDTO(
                    "running", "farm1", "pod-alice-abc",
                    List.of(new CreatePodResponseDTO.PortInfo("ssh", 22, 30022))
            );
            stubWebClientPost(Mono.just(expected));

            // When
            CreatePodResponseDTO result = podService.createPod("alice");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.podName()).isEqualTo("pod-alice-abc");
            assertThat(result.node()).isEqualTo("farm1");
            assertThat(result.ports()).hasSize(1);
            assertThat(result.ports().get(0).usagePurpose()).isEqualTo("ssh");
        }

        @Test
        @DisplayName("포트가 없는 정상 응답도 정상 처리된다")
        void createPod_noPortsInResponse_returnsDto() {
            // Given
            CreatePodResponseDTO expected = new CreatePodResponseDTO(
                    "running", "lab1", "pod-bob-xyz", List.of()
            );
            stubWebClientPost(Mono.just(expected));

            // When
            CreatePodResponseDTO result = podService.createPod("bob");

            // Then
            assertThat(result.podName()).isEqualTo("pod-bob-xyz");
            assertThat(result.ports()).isEmpty();
        }
    }

    @Nested
    @DisplayName("C-3: createPod null/빈 응답 처리")
    class CreatePodNullResponse {

        @Test
        @DisplayName("외부 API가 null을 반환하면 POD_CREATION_FAILED 예외가 발생한다")
        void createPod_nullResponse_throwsBusinessException() {
            // Given - bodyToMono가 null을 반환하는 경우 (빈 응답 바디)
            stubWebClientPost(Mono.empty());

            // When & Then
            assertThatThrownBy(() -> podService.createPod("charlie"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POD_CREATION_FAILED);
        }

        @Test
        @DisplayName("podName이 null인 응답도 POD_CREATION_FAILED 예외가 발생한다")
        void createPod_responsePodNameNull_throwsBusinessException() {
            // Given - podName 필드가 null인 응답
            CreatePodResponseDTO badResponse = new CreatePodResponseDTO(
                    "unknown", "farm1", null, List.of()
            );
            stubWebClientPost(Mono.just(badResponse));

            // When & Then
            assertThatThrownBy(() -> podService.createPod("dave"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POD_CREATION_FAILED);
        }
    }

    @Nested
    @DisplayName("createPod 외부 API 오류 처리")
    class CreatePodApiError {

        @Test
        @DisplayName("BusinessException이 발생하면 그대로 재전파된다")
        void createPod_businessException_rethrows() {
            // Given
            when(webClient.post()).thenReturn(postUriSpec);
            when(postUriSpec.uri(anyString())).thenReturn(postBodySpec);
            doReturn(postHeadersSpec).when(postBodySpec).bodyValue(any());
            when(postHeadersSpec.retrieve()).thenReturn(postResponseSpec);
            when(postResponseSpec.onStatus(any(), any())).thenReturn(postResponseSpec);
            doReturn(Mono.error(new BusinessException(ErrorCode.POD_CREATION_FAILED)))
                    .when(postResponseSpec).bodyToMono(CreatePodResponseDTO.class);

            // When & Then
            assertThatThrownBy(() -> podService.createPod("eve"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POD_CREATION_FAILED);
        }

        @Test
        @DisplayName("예상치 못한 RuntimeException은 POD_CREATION_FAILED로 래핑된다")
        void createPod_unexpectedRuntimeException_wrapsToBusinessException() {
            // Given
            when(webClient.post()).thenReturn(postUriSpec);
            when(postUriSpec.uri(anyString())).thenReturn(postBodySpec);
            doReturn(postHeadersSpec).when(postBodySpec).bodyValue(any());
            when(postHeadersSpec.retrieve()).thenReturn(postResponseSpec);
            when(postResponseSpec.onStatus(any(), any())).thenReturn(postResponseSpec);
            doReturn(Mono.error(new RuntimeException("네트워크 오류")))
                    .when(postResponseSpec).bodyToMono(CreatePodResponseDTO.class);

            // When & Then
            assertThatThrownBy(() -> podService.createPod("frank"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POD_CREATION_FAILED);
        }
    }
}
