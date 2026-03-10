package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * [임시] 인프라 Pod 생성 API 미완성 기간 동안 사용하는 더미 구현체
 * 인프라 연동 완료 후 이 파일을 삭제하면 PodService가 자동으로 활성화됩니다.
 */
@Slf4j
@Service
@Primary
@Profile({"local", "stub"})
public class PodServiceStub extends PodService {

    public PodServiceStub(@Qualifier("configWebClient") WebClient webClient) {
        super(webClient);
    }

    @Override
    public CreatePodResponseDTO createPod(String username) {
        log.warn("[MOCK] Pod 생성 더미 응답 반환: 사용자: {}", username);
        return new CreatePodResponseDTO("running", "farm1", "pod-" + username + "-mock",
                List.of(new CreatePodResponseDTO.PortInfo("ssh", 22, 30022),
                        new CreatePodResponseDTO.PortInfo("jupyter", 8888, 30888)));
    }
}
