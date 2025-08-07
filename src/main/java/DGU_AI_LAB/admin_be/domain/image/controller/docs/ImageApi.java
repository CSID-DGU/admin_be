package DGU_AI_LAB.admin_be.domain.image.controller.docs;

import DGU_AI_LAB.admin_be.domain.image.dto.request.ImageCreateRequest;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "이미지 관리", description = "컨테이너 이미지 생성 및 조회 API")
public interface ImageApi {

    @Operation(summary = "이미지 생성", description = "새로운 컨테이너 이미지를 등록합니다.")
    @ApiResponse(responseCode = "201", description = "이미지 생성 성공",
            content = @Content(schema = @Schema(implementation = SuccessResponse.class)))
    ResponseEntity<?> createImage(
            @RequestBody @Valid ImageCreateRequest request
    );

    @Operation(summary = "단일 이미지 조회", description = "이미지 ID로 단일 이미지를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "이미지 조회 성공",
            content = @Content(schema = @Schema(implementation = SuccessResponse.class)))
    ResponseEntity<?> getImageById(
            @Parameter(description = "이미지 ID", example = "1") @PathVariable Long id
    );

    @Operation(summary = "전체 이미지 목록 조회", description = "등록된 모든 이미지를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "이미지 목록 조회 성공",
            content = @Content(schema = @Schema(implementation = SuccessResponse.class)))
    ResponseEntity<?> getAllImages();
}