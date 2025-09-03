package DGU_AI_LAB.admin_be.domain.pod.controller.docs;

import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "0. Kubernetes Pods", description = "쿠버네티스 Pod 조회 API")
public interface PodApi {

    @Operation(
            summary = "전체 Pod 목록 조회"
    )
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공"
    )
    @ApiResponse(
            responseCode = "500",
            description = "서버 오류",
            content = @Content
    )
    List<String> getPods();

    @Operation(
            summary = "단일 pod 정보 조회"
    )
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = PodResponseDTO.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = "해당 이름의 Pod를 찾을 수 없음",
            content = @Content
    )
    @ApiResponse(
            responseCode = "500",
            description = "서버 오류",
            content = @Content
    )
    PodResponseDTO getPodDetail(
            @Parameter(description = "조회할 Pod의 이름")
            String podName
    );
}