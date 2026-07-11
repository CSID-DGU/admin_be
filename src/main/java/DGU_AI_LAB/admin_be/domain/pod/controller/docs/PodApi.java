package DGU_AI_LAB.admin_be.domain.pod.controller.docs;

import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "3. 관리자 시스템", description = "컨테이너 이미지, K8s Pod, 알림 템플릿 관리 API")
public interface PodApi {

    @Operation(summary = "전체 Pod 목록 조회", description = "default 네임스페이스의 모든 Pod 이름을 조회합니다.")
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
}