package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.containerImage.dto.response.ContainerImageResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
@Schema(description = "사용자 대시보드 서버 목록 응답 DTO")
public record UserServerResponseDTO(
        @Schema(description = "서버 신청 고유 ID", example = "1")
        Long requestId,
        @Schema(description = "할당된 서버 주소 (승인 후 유효)", example = "192.168.1.100", nullable = true)
        String serverAddress,
        @Schema(description = "서버 사용 만료일", example = "2025-12-31T23:59:59")
        LocalDateTime expiresAt,
        @Schema(description = "할당된 볼륨 크기 (GiB)", example = "100")
        Long volumeSizeGiB,
        @Schema(description = "할당된 CPU 코어 수", example = "8")
        Integer cpuCoreCount,
        // TODO: 용도가 무엇인지 모르겠으나 GiB로 통일하는 것이 좋아보임.
        @Schema(description = "할당된 메모리 크기 (GB)", example = "32")
        Integer memoryGB,
        @Schema(description = "할당된 리소스 그룹명 (GPU 스펙 묶음)", example = "RTX 3090 D6 24GB 그룹")
        String resourceGroupName,
        @Schema(description = "컨테이너 이미지 정보")
        ContainerImageResponseDTO containerImage,
        @Schema(description = "서버 신청 상태", example = "FULFILLED", allowableValues = {"PENDING", "FULFILLED", "DENIED", "MODIFICATION_REQUESTED", "MODIFICATION_APPROVED", "MODIFICATION_REJECTED"})
        Status status
) {
    public static UserServerResponseDTO fromEntity(Request request, String serverAddress, Integer cpuCoreCount, Integer memoryGB, String resourceGroupName, ContainerImageResponseDTO containerImage) {
        return UserServerResponseDTO.builder()
                .requestId(request.getRequestId())
                .serverAddress(serverAddress)
                .expiresAt(request.getExpiresAt())
                .volumeSizeGiB(request.getVolumeSizeGiB())
                .cpuCoreCount(cpuCoreCount)
                .memoryGB(memoryGB)
                .resourceGroupName(resourceGroupName)
                .containerImage(containerImage)
                .status(request.getStatus())
                .build();
    }
}