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

    @Mock private WebClient configWebClient;
    @Mock private WebClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;
    @Mock private WebClient.RequestHeadersUriSpec<?> deleteUriSpec;
    @Mock private RequestRepository requestRepository;
    @Mock private OperationJobService operationJobService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        podService = new PodService(webClient, configWebClient, requestRepository, operationJobService);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);

        doReturn(requestHeadersUriSpec).when(configWebClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(anyString());
        doReturn(deleteUriSpec).when(webClient).delete();
        doReturn(requestHeadersSpec).when(deleteUriSpec).uri(anyString(), any(Object[].class));
    }

    // ───────────────────────────────────────────────────────────────
    // deletePod
    // ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("deletePod")
    class DeletePod {

        @Test
        @DisplayName("podName이 null이면 회수 작업을 등록하지 않고 정상 반환한다")
        void deletePod_skips_whenPodNameIsNull() {
            assertThatCode(() -> podService.deletePod(null, 1L))
                    .doesNotThrowAnyException();

            verifyNoInteractions(operationJobService);
        }

        @Test
        @DisplayName("신청 번호와 Pod 이름으로 회수 작업을 등록하고 끝날 때까지 기다린다 — 계정은 회수하지 않는다")
        void deletePod_revokesPodThroughJob() {
            podService.deletePod("ailab-testuser-abcd", 4821L);

            ArgumentCaptor<RevokeRegisterRequestDTO> captor = ArgumentCaptor.forClass(RevokeRegisterRequestDTO.class);
            verify(operationJobService).revokeAndWait(captor.capture(), eq(ErrorCode.POD_DELETION_FAILED));
            assertThat(captor.getValue().requestId()).isEqualTo(4821L);
            assertThat(captor.getValue().podName()).isEqualTo("ailab-testuser-abcd");
            assertThat(captor.getValue().deleteAccount()).isFalse();
            verify(webClient, never()).post();
        }

        @Test
        @DisplayName("신청 번호가 없으면 회수하지 않고 실패한다 — 고아 Pod는 deleteOrphanPod를 쓴다")
        void deletePod_requiresRequestId() {
            assertThatThrownBy(() -> podService.deletePod("orphan-pod", null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POD_DELETION_FAILED);

            verifyNoInteractions(operationJobService);
        }

        @Test
        @DisplayName("회수 작업이 실패하면 예외를 그대로 전파한다")
        void deletePod_propagatesJobFailure() {
            doThrow(new BusinessException("회수 작업 실패", ErrorCode.POD_DELETION_FAILED))
                    .when(operationJobService).revokeAndWait(any(), any());

            assertThatThrownBy(() -> podService.deletePod("error-pod", 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("회수 작업 실패");
        }

        @Test
        @DisplayName("deletePod 메서드에 @Transactional 어노테이션이 없다")
        void deletePod_hasNoTransactionalAnnotation() throws NoSuchMethodException {
            var method = PodService.class.getMethod("deletePod", String.class, Long.class);
            var txAnnotation = method.getAnnotation(Transactional.class);

            assertThat(txAnnotation)
                    .as("Propagation.MANDATORY 등 트랜잭션 어노테이션이 제거되어야 함")
                    .isNull();
        }
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
