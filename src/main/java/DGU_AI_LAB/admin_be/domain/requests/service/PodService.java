package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.webclient.WebClientErrorHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 신청에 딸리지 않은 고아 Pod를 지우는 Infra API 호출 서비스.
 * 신청에 딸린 컨테이너의 생성·회수는 config-server 작업(JobClient)이 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PodService {

    private final @Qualifier("podWebClient") WebClient webClient;
    private final RequestRepository requestRepository;

    /** config-server generate_pod_name 규칙: ailab-{username}-{uuid hex 8자리}. */
    private static final Pattern MANAGED_POD_NAME = Pattern.compile("^ailab-(.+)-[0-9a-f]{8}$");

    /** 새 Pod가 이미 떴을 수 있지만 신청의 podName에는 아직 안 적힌 상태 — 생성 작업, 노드 이동 작업. */
    private static final List<Status> POD_JOB_STATUSES = List.of(Status.PROCESSING, Status.MIGRATING);

    /**
     * DB에 대응하는 Request가 전혀 없는 고아 Pod를 관리자가 k8s에서 직접 지운다.
     * config-server가 pod가 뜬 경로를 우회해서(예: 자동화 시스템을 거치지 않은 수동 생성)
     * DB에 흔적이 안 남는 경우가 실제로 있었다 — 그런 Pod는 User/Request 삭제 흐름으로는
     * 절대 안 지워지므로 이 경로가 유일한 정리 수단이다. 신청 이력이 있는 Pod는 계정 삭제/
     * NodePort 정리 등 딸린 정리 작업이 있으므로 이 경로로 지우지 못하게 막는다 — 그런
     * Pod는 사용자 삭제/신청 만료 같은 정식 경로를 거쳐야 한다.
     * 생성·노드 이동 작업은 작업이 끝나야 podName을 적으므로, 그 사이의 Pod는 신청 이력이 없어
     * 보여도 작업 중인 Pod다 — 이름의 사용자에게 도는 작업이 있으면 함께 거부한다.
     */
    public void deleteOrphanPod(String podName) {
        if (requestRepository.existsByPodName(podName)) {
            throw new BusinessException(ErrorCode.POD_NOT_ORPHAN);
        }
        Optional<String> owner = managedPodOwner(podName);
        if (owner.isPresent()
                && requestRepository.existsByUser_UbuntuUsernameAndStatusIn(owner.get(), POD_JOB_STATUSES)) {
            throw new BusinessException(ErrorCode.POD_JOB_IN_PROGRESS);
        }
        try {
            log.info("고아 Pod 삭제 API 요청 시작: {}", podName);

            WebClientErrorHandler.onError(
                            webClient.delete()
                                    .uri("/pods/{podName}", podName)
                                    .retrieve(),
                            (status, body) -> {
                                if (status == HttpStatus.NOT_FOUND) {
                                    log.warn("Pod가 이미 존재하지 않음 (404): {}", podName);
                                    return null;
                                }
                                log.error("Pod 삭제 실패 ({}): {}", status, body);
                                return WebClientErrorHandler.rejectedOr(status, body, "Pod 삭제 실패", ErrorCode.POD_DELETION_FAILED);
                            }
                    )
                    .bodyToMono(Map.class)
                    .block();

            log.info("고아 Pod 삭제 API 요청 성공: {}", podName);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("고아 Pod 삭제 API 호출 중 예기치 않은 오류: {}", podName, e);
            throw new BusinessException("Pod 삭제 API 호출 오류", ErrorCode.POD_DELETION_FAILED);
        }
    }

    private static Optional<String> managedPodOwner(String podName) {
        Matcher m = MANAGED_POD_NAME.matcher(podName);
        return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
