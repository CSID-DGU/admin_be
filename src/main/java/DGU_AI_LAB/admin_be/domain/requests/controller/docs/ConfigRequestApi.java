package DGU_AI_LAB.admin_be.domain.requests.controller.docs;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.AcceptInfoResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "0. Config Server용 API", description = "내부 Config Server 연동을 위한 API")
@RequestMapping("/api/requests/config")
public interface ConfigRequestApi {

    @Operation(summary = "우분투 계정명 사용 가능 여부 확인", description = "지정된 우분투 계정명이 사용 가능한지 확인합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            examples = {
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "사용 가능",
                                            value = "{\"available\": true}"
                                    ),
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "사용 불가능",
                                            value = "{\"available\": false}"
                                    )
                            }
                    )
            )
    })
    @GetMapping("/check-username")
    ResponseEntity<?> checkUbuntuUsername(
            @RequestParam @Parameter(description = "확인할 우분투 계정명", example = "toni") String username
    );

    @Operation(summary = "요청 승인 정보 조회", description = "지정된 우분투 계정명에 대한 승인된 요청 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "404", description = "승인된 요청을 찾을 수 없음")
    })
    @GetMapping("/{username}")
    ResponseEntity<AcceptInfoResponseDTO> getAcceptInfo(
            @PathVariable @Parameter(description = "승인 정보 조회 대상 계정명", example = "toni") String username
    );
}