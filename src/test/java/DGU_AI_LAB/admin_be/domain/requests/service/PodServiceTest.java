package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.RevokeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    private PodService podService;

    @Mock private WebClient webClient;
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    @Mock private WebClient.RequestHeadersUriSpec<?> deleteUriSpec;
    @Mock private RequestRepository requestRepository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        podService = new PodService(webClient, requestRepository);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);

        doReturn(deleteUriSpec).when(webClient).delete();
        doReturn(requestHeadersSpec).when(deleteUriSpec).uri(anyString(), any(Object[].class));
    }

    @Nested
    @DisplayName("deleteOrphanPod")
    class DeleteOrphanPod {

        @Test
        @DisplayName("대응하는 Request가 없으면 실제 Pod 삭제를 호출한다")
        void deleteOrphanPod_noMatchingRequest_deletesPod() {
            when(requestRepository.existsByPodName("orphan-pod")).thenReturn(false);
            when(responseSpec.bodyToMono(Map.class))
                    .thenReturn(Mono.just(Map.of("status", "ok")));

            assertThatCode(() -> podService.deleteOrphanPod("orphan-pod"))
                    .doesNotThrowAnyException();

            verify(webClient).delete();
        }

        @Test
        @DisplayName("대응하는 Request가 있으면 삭제를 거부하고 실제 Pod 삭제는 호출하지 않는다")
        void deleteOrphanPod_matchingRequestExists_rejectsWithoutDeleting() {
            when(requestRepository.existsByPodName("tracked-pod")).thenReturn(true);

            assertThatThrownBy(() -> podService.deleteOrphanPod("tracked-pod"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POD_NOT_ORPHAN);

            verify(webClient, never()).delete();
        }
    }

    // ───────────────────────────────────────────────────────────────
    // createPod
    // ───────────────────────────────────────────────────────────────
}
