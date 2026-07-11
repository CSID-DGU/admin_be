package DGU_AI_LAB.admin_be.domain.containerImage.controller.docs;

import DGU_AI_LAB.admin_be.domain.containerImage.dto.request.ContainerImageCreateRequest;
import DGU_AI_LAB.admin_be.domain.containerImage.dto.response.ContainerImageResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@Tag(name = "3. 관리자 시스템", description = "컨테이너 이미지, K8s Pod, 알림 템플릿 관리 API")
public interface ContainerImageApi {

    @Operation(summary = "컨테이너 이미지 등록", description = "새로운 컨테이너 이미지를 등록합니다. (관리자 전용)")
    @ApiResponse(responseCode = "200", description = "등록 성공",
            content = @Content(schema = @Schema(implementation = ContainerImageResponseDTO.class)))
    ResponseEntity<ContainerImageResponseDTO> createImage(
            @RequestBody @Valid ContainerImageCreateRequest request
    );

    @Operation(summary = "단일 이미지 조회", description = "이미지 ID로 단일 이미지를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "이미지 조회 성공",
            content = @Content(schema = @Schema(implementation = ContainerImageResponseDTO.class)))
    ResponseEntity<ContainerImageResponseDTO> getImageById(
            @PathVariable Long id
    );

    @Operation(summary = "전체 이미지 목록 조회", description = "등록된 모든 이미지를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "이미지 목록 조회 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ContainerImageResponseDTO.class))))
    ResponseEntity<List<ContainerImageResponseDTO>> getAllImages();
}