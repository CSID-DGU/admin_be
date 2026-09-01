package DGU_AI_LAB.admin_be.domain.pod.controller.docs;

import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodEventDTO;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

@Tag(name = "3. 관리자 시스템", description = "컨테이너 이미지, K8s Pod, 알림 템플릿 관리 API")
public interface PodApi {

    @Operation(summary = "전체 Pod 목록 조회", description = "ailab-infra 네임스페이스의 모든 Pod 이름을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "500", description = "K8s 연동 오류", content = @Content)
    List<String> getPods();

    @Operation(summary = "Pod 상세 조회", description = "Pod 이름으로 특정 Pod의 상세 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = PodResponseDTO.class)))
    @ApiResponse(responseCode = "404", description = "해당 이름의 Pod를 찾을 수 없음", content = @Content)
    PodResponseDTO getPodDetail(
            @Parameter(description = "Pod 이름") String podName
    );

    @Operation(summary = "Pod 로그 조회", description = "최근 500줄의 컨테이너 로그를 조회합니다. container를 생략하면 첫 번째 컨테이너를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "해당 이름의 Pod를 찾을 수 없음", content = @Content)
    Map<String, String> getPodLogs(
            @Parameter(description = "Pod 이름") String podName,
            @Parameter(description = "컨테이너 이름 (생략 시 첫 번째 컨테이너)") String container
    );

    @Operation(summary = "Pod 이벤트 조회", description = "해당 Pod와 관련된 최신 K8s 이벤트 최대 50건을 조회합니다 (스케줄링 실패, 이미지 Pull 실패 등 원인 파악용).")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = PodEventDTO.class)))
    List<PodEventDTO> getPodEvents(
            @Parameter(description = "Pod 이름") String podName
    );
}