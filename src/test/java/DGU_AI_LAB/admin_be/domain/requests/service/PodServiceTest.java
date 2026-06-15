package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.InfraOperationException;
import DGU_AI_LAB.admin_be.error.exception.InfraOperationException.InfraStep;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PodServiceTest {

    @Mock private WebClient mockWebClient;
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    private PodService podService;

    @BeforeEach
    void setUp() {
        podService = new PodService(mockWebClient);
    }

    @SuppressWarnings("unchecked")
    private void stubPostChain() {
        when(mockWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    }

    @SuppressWarnings("unchecked")
    private Function<ClientResponse, Mono<? extends Throwable>> captureOnStatusHandler() {
        ArgumentCaptor<Function<ClientResponse, Mono<? extends Throwable>>> handlerCaptor =
                ArgumentCaptor.forClass(Function.class);
        verify(responseSpec).onStatus(any(Predicate.class), handlerCaptor.capture());
        return handlerCaptor.getValue();
    }

    private InfraOperationException invokeHandler(
            Function<ClientResponse, Mono<? extends Throwable>> handler,
            HttpStatus status,
            String body
    ) {
        ClientResponse mockResponse = mock(ClientResponse.class);
        when(mockResponse.statusCode()).thenReturn(status);
        when(mockResponse.bodyToMono(String.class)).thenReturn(Mono.just(body));
        try {
            handler.apply(mockResponse).block();
            return null;
        } catch (InfraOperationException e) {
            return e;
        }
    }

    @Nested
    @DisplayName("createPod()")
    class CreatePodTest {

        @Test
        @DisplayName("정상 응답 시 CreatePodResponseDTO를 반환한다")
        void success_returnsResponse() {
            stubPostChain();
            CreatePodResponseDTO expected = new CreatePodResponseDTO(
                    "created", "gpu-node-1", "ailab-testuser-abc123",
                    List.of(new CreatePodResponseDTO.PortInfo("ssh", 22, 30022))
            );
            when(responseSpec.bodyToMono(CreatePodResponseDTO.class))
                    .thenReturn(Mono.just(expected));

            CreatePodResponseDTO result = podService.createPod("testuser");

            assertThat(result.podName()).isEqualTo("ailab-testuser-abc123");
            assertThat(result.node()).isEqualTo("gpu-node-1");
            assertThat(result.ports()).hasSize(1);
        }

        @Test
        @DisplayName("null 응답이면 InfraOperationException(EMPTY_RESPONSE)을 던진다")
        void nullResponse_throwsException() {
            stubPostChain();
            when(responseSpec.bodyToMono(CreatePodResponseDTO.class))
                    .thenReturn(Mono.empty());

            assertThatThrownBy(() -> podService.createPod("testuser"))
                    .isInstanceOf(InfraOperationException.class)
                    .satisfies(ex -> {
                        InfraOperationException infra = (InfraOperationException) ex;
                        assertThat(infra.getErrorCode()).isEqualTo(ErrorCode.POD_CREATION_FAILED);
                        assertThat(infra.getInfraError()).isEqualTo("EMPTY_RESPONSE");
                    });
        }

        @Test
        @DisplayName("infra가 POD_ALREADY_EXISTS 에러를 반환하면 ErrorCode.POD_ALREADY_EXISTS로 매핑한다")
        void podAlreadyExists_mapsToCorrectErrorCode() {
            stubPostChain();
            when(responseSpec.bodyToMono(CreatePodResponseDTO.class))
                    .thenReturn(Mono.just(new CreatePodResponseDTO("created", "n", "p", List.of())));
            podService.createPod("testuser");

            Function<ClientResponse, Mono<? extends Throwable>> handler = captureOnStatusHandler();
            String infraBody = """
                    {"step":"CHECK_EXISTING_POD","error":"POD_ALREADY_EXISTS","detail":"pod already exists","progress":{}}
                    """;
            InfraOperationException ex = invokeHandler(handler, HttpStatus.CONFLICT, infraBody);

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.POD_ALREADY_EXISTS);
            assertThat(ex.getStep()).isEqualTo(InfraStep.CHECK_EXISTING_POD);
        }

        @Test
        @DisplayName("infra가 POD_READY_TIMEOUT 에러를 반환하면 ErrorCode.POD_READY_TIMEOUT으로 매핑한다")
        void podReadyTimeout_mapsToCorrectErrorCode() {
            stubPostChain();
            when(responseSpec.bodyToMono(CreatePodResponseDTO.class))
                    .thenReturn(Mono.just(new CreatePodResponseDTO("created", "n", "p", List.of())));
            podService.createPod("testuser");

            Function<ClientResponse, Mono<? extends Throwable>> handler = captureOnStatusHandler();
            String infraBody = """
                    {"step":"WAIT_POD_READY","error":"POD_READY_TIMEOUT","detail":"pod failed to start","progress":{"nodeportsReleased":true,"podDeleted":true}}
                    """;
            InfraOperationException ex = invokeHandler(handler, HttpStatus.INTERNAL_SERVER_ERROR, infraBody);

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.POD_READY_TIMEOUT);
            assertThat(ex.getProgress()).containsEntry("nodeportsReleased", true);
        }

        @Test
        @DisplayName("infra가 NODE_SELECTION_FAILED 에러를 반환하면 ErrorCode.NODE_SELECTION_FAILED로 매핑한다")
        void nodeSelectionFailed_mapsToCorrectErrorCode() {
            stubPostChain();
            when(responseSpec.bodyToMono(CreatePodResponseDTO.class))
                    .thenReturn(Mono.just(new CreatePodResponseDTO("created", "n", "p", List.of())));
            podService.createPod("testuser");

            Function<ClientResponse, Mono<? extends Throwable>> handler = captureOnStatusHandler();
            String infraBody = """
                    {"step":"SELECT_NODE","error":"NODE_SELECTION_FAILED","detail":"no suitable node"}
                    """;
            InfraOperationException ex = invokeHandler(handler, HttpStatus.INTERNAL_SERVER_ERROR, infraBody);

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NODE_SELECTION_FAILED);
        }

        @Test
        @DisplayName("infra가 USER_CONFIG_NOT_FOUND 에러를 반환하면 ErrorCode.USER_CONFIG_NOT_FOUND로 매핑한다")
        void userConfigNotFound_mapsToCorrectErrorCode() {
            stubPostChain();
            when(responseSpec.bodyToMono(CreatePodResponseDTO.class))
                    .thenReturn(Mono.just(new CreatePodResponseDTO("created", "n", "p", List.of())));
            podService.createPod("testuser");

            Function<ClientResponse, Mono<? extends Throwable>> handler = captureOnStatusHandler();
            String infraBody = """
                    {"step":"FETCH_USER_CONFIG","error":"USER_CONFIG_NOT_FOUND","detail":"user not found in WAS"}
                    """;
            InfraOperationException ex = invokeHandler(handler, HttpStatus.NOT_FOUND, infraBody);

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_CONFIG_NOT_FOUND);
        }

        @Test
        @DisplayName("알 수 없는 infra error면 POD_CREATION_FAILED로 매핑한다")
        void unknownInfraError_defaultsToCreationFailed() {
            stubPostChain();
            when(responseSpec.bodyToMono(CreatePodResponseDTO.class))
                    .thenReturn(Mono.just(new CreatePodResponseDTO("created", "n", "p", List.of())));
            podService.createPod("testuser");

            Function<ClientResponse, Mono<? extends Throwable>> handler = captureOnStatusHandler();
            String infraBody = """
                    {"step":"BUILD_POD_SPEC","error":"SOME_UNKNOWN_ERROR","detail":"something went wrong"}
                    """;
            InfraOperationException ex = invokeHandler(handler, HttpStatus.INTERNAL_SERVER_ERROR, infraBody);

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.POD_CREATION_FAILED);
        }
    }

    @Nested
    @DisplayName("deletePod()")
    class DeletePodTest {

        @Test
        @DisplayName("pod_name이 null이면 삭제를 건너뛴다")
        void nullPodName_skips() {
            podService.deletePod(null);

            verify(mockWebClient, never()).post();
        }

        @Test
        @DisplayName("already_absent=true 응답은 정상 처리된다")
        @SuppressWarnings("unchecked")
        void alreadyAbsent_treatedAsSuccess() {
            stubPostChain();
            Map<String, Object> responseBody = Map.of(
                    "status", "deleted",
                    "pod_name", "ailab-testuser-abc",
                    "already_absent", true,
                    "progress", Map.of("servicesDeleted", true, "nodeportsReleased", true, "podDeleted", true)
            );
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(responseBody));

            podService.deletePod("ailab-testuser-abc");

            verify(mockWebClient).post();
        }

        @Test
        @DisplayName("정상 삭제 응답을 처리한다")
        @SuppressWarnings("unchecked")
        void normalDelete_succeeds() {
            stubPostChain();
            Map<String, Object> responseBody = Map.of(
                    "status", "deleted",
                    "pod_name", "ailab-testuser-abc",
                    "progress", Map.of("servicesDeleted", true, "nodeportsReleased", true, "podDeleted", true)
            );
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(responseBody));

            podService.deletePod("ailab-testuser-abc");

            verify(mockWebClient).post();
        }

        @Test
        @DisplayName("infra가 POD_DELETE_TIMEOUT 에러를 반환하면 ErrorCode.POD_DELETE_TIMEOUT으로 매핑한다")
        @SuppressWarnings("unchecked")
        void deleteTimeout_mapsToCorrectErrorCode() {
            stubPostChain();
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of("status", "deleted")));
            podService.deletePod("ailab-testuser-abc");

            Function<ClientResponse, Mono<? extends Throwable>> handler = captureOnStatusHandler();
            String infraBody = """
                    {"step":"DELETE_POD","error":"POD_DELETE_TIMEOUT","detail":"pod deletion did not complete within timeout","progress":{"servicesDeleted":true,"nodeportsReleased":true,"podDeleteRequested":true,"podDeleted":false}}
                    """;
            InfraOperationException ex = invokeHandler(handler, HttpStatus.INTERNAL_SERVER_ERROR, infraBody);

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.POD_DELETE_TIMEOUT);
            assertThat(ex.getStep()).isEqualTo(InfraStep.DELETE_POD);
            assertThat(ex.getProgress())
                    .containsEntry("podDeleted", false)
                    .containsEntry("servicesDeleted", true);
        }

        @Test
        @DisplayName("일반 삭제 실패는 POD_DELETION_FAILED로 매핑한다")
        @SuppressWarnings("unchecked")
        void genericDeleteError_defaultsToDeletionFailed() {
            stubPostChain();
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of("status", "deleted")));
            podService.deletePod("ailab-testuser-abc");

            Function<ClientResponse, Mono<? extends Throwable>> handler = captureOnStatusHandler();
            String infraBody = """
                    {"step":"DELETE_POD","error":"POD_DELETE_FAILED","detail":"k8s error"}
                    """;
            InfraOperationException ex = invokeHandler(handler, HttpStatus.INTERNAL_SERVER_ERROR, infraBody);

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.POD_DELETION_FAILED);
        }
    }
}
