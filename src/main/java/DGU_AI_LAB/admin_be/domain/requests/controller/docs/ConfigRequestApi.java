package DGU_AI_LAB.admin_be.domain.requests.controller.docs;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.AcceptInfoResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "0. 내부 전용 API", description = "인프라·Config 서버 전용 내부 API (인증 불필요, 내부망 전용)")
@RequestMapping("/api/requests/config")
public interface ConfigRequestApi {

    @Operation(summary = "우분투 계정명 사용 가능 여부 확인", description = "지정된 우분투 계정명이 현재 시스템에서 사용 가능한지 확인합니다.")
    @ApiResponse(responseCode = "200", description = "사용 가능 여부 반환 — {\"available\": true/false}")
    @GetMapping("/check-username")
    ResponseEntity<?> checkUbuntuUsername(
            @RequestParam @Parameter(description = "확인할 우분투 계정명", example = "toni") String username
    );

    @Operation(summary = "승인 정보 조회", description = "우분투 계정명으로 승인된 서버 사용 신청 정보를 조회합니다. Config Server에서 계정 설정 적용 시 호출합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "해당 계정명의 승인 정보가 없음")
    @GetMapping("/{username}")
    ResponseEntity<AcceptInfoResponseDTO> getAcceptInfo(
            @PathVariable @Parameter(description = "우분투 계정명", example = "toni") String username
    );
}