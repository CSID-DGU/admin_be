package DGU_AI_LAB.admin_be.domain.requests.controller.docs;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.AcceptInfoResponseDTO;
import DGU_AI_LAB.admin_be.global.auth.InternalTokenFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "0. 내부 전용 API", description = "클러스터별 내부 토큰으로 인증하는 Config 서버 전용 API")
@RequestMapping("/api/requests/config")
public interface ConfigRequestApi {

    @Operation(summary = "우분투 계정명 사용 가능 여부 확인", description = "지정된 우분투 계정명이 현재 시스템에서 사용 가능한지 확인합니다. 로그인(JWT)이 필요합니다.")
    @ApiResponse(responseCode = "200", description = "사용 가능 여부 반환 — {\"available\": true/false}")
    @GetMapping("/check-username")
    ResponseEntity<?> checkUbuntuUsername(
            @RequestParam @Parameter(description = "확인할 우분투 계정명", example = "toni") String username
    );

    @Operation(summary = "승인 정보 조회 (레거시)", description = "우분투 계정명으로 승인된 서버 사용 신청 정보를 조회합니다. " +
            "한 사용자가 신청을 여러 개 동시에 가질 수 있어 \"가장 최근 열린 신청\"을 반환합니다 — 가능하면 " +
            "getAcceptInfoByRequestId를 쓰세요. Config Server에서 계정 설정 적용 시 호출합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "403", description = "내부 토큰·클러스터 또는 신청 클러스터 불일치")
    @ApiResponse(responseCode = "404", description = "해당 계정명의 승인 정보가 없음")
    @GetMapping("/{username}")
    ResponseEntity<AcceptInfoResponseDTO> getAcceptInfo(
            @RequestAttribute(InternalTokenFilter.VERIFIED_CLUSTER_ATTRIBUTE) String clusterId,
            @PathVariable @Parameter(description = "우분투 계정명", example = "toni") String username
    );

    @Operation(summary = "승인 정보 조회 (requestId 기준)", description = "신청 ID로 정확히 그 신청 하나의 정보를 조회합니다. " +
            "한 사용자가 신청을 여러 개 동시에 가져도 모호함이 없습니다. Config Server에서 Pod 생성·마이그레이션 시 호출합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "403", description = "내부 토큰·클러스터 또는 신청 클러스터 불일치")
    @ApiResponse(responseCode = "404", description = "해당 requestId의 승인 정보가 없음")
    @GetMapping("/by-request/{requestId}")
    ResponseEntity<AcceptInfoResponseDTO> getAcceptInfoByRequestId(
            @RequestAttribute(InternalTokenFilter.VERIFIED_CLUSTER_ATTRIBUTE) String clusterId,
            @PathVariable @Parameter(description = "신청 ID", example = "42") Long requestId
    );
}
