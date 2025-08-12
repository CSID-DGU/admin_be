package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record UserServerResponseDTO(
        Long requestId,
        String serverAddress,
        LocalDateTime expiresAt,
        Long volumeSizeGB,
        String cudaVersion,
        Integer cpuCoreCount,
        Integer memoryGB,
        String resourceGroupName,
        Status status
) {
    public static UserServerResponseDTO fromEntity(Request request, String serverAddress, Integer cpuCoreCount, Integer memoryGB, String resourceGroupName) {
        return UserServerResponseDTO.builder()
                .requestId(request.getRequestId())
                .serverAddress(serverAddress)
                .expiresAt(request.getExpiresAt())
                .volumeSizeGB(request.getVolumeSizeByte() / (1024L * 1024 * 1024)) // Byte를 GB로 변환
                .cudaVersion(request.getCudaVersion())
                .cpuCoreCount(cpuCoreCount)
                .memoryGB(memoryGB)
                .resourceGroupName(resourceGroupName)
                .status(request.getStatus())
                .build();
    }
}