package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
@Schema(description = "서버 신청 상세 응답 DTO")
public record SaveRequestResponseDTO(
        @Schema(description = "서버 신청 고유 ID", example = "42")
        Long requestId,
        @Schema(description = "리소스 그룹 ID", example = "1")
        Integer resourceGroupId,
        @Schema(description = "리소스 그룹 정보")
        AdminResourceGroupInfo resourceGroup,
        @Schema(description = "신청자 정보")
        AdminUserInfo user,
        @Schema(description = "컨테이너 이미지 ID", example = "1")
        Long imageId,
        @Schema(description = "컨테이너 이미지 이름", example = "cuda")
        String imageName,
        @Schema(description = "컨테이너 이미지 버전", example = "11.8")
        String imageVersion,
        @Schema(description = "Ubuntu 사용자명", example = "test2014")
        String ubuntuUsername,
        @Schema(description = "Ubuntu UID", example = "10001", nullable = true)
        Long ubuntuUid,
        @Schema(description = "Ubuntu GID 목록", example = "[1005, 1006]")
        List<Long> ubuntuGids,
        @Schema(description = "볼륨 크기 (GiB)", example = "20")
        Long volumeSizeGiB,
        @Schema(description = "사용 목적", example = "딥러닝 모델 학습")
        String usagePurpose,
        @Schema(description = "폼 응답 (JSON)", example = "{\"question\": \"answer\"}")
        @JsonRawValue String formAnswers,
        @Schema(description = "서버 만료 일시", example = "2026-03-02T06:17:29")
        LocalDateTime expiresAt,
        @Schema(description = "처리 상태", example = "PENDING", allowableValues = {"PENDING", "FULFILLED", "DENIED", "MODIFICATION_REQUESTED", "MODIFICATION_APPROVED", "MODIFICATION_REJECTED"})
        Status status,
        @Schema(description = "승인 일시", example = "2026-03-02T15:36:29", nullable = true)
        LocalDateTime approvedAt,
        @Schema(description = "관리자 코멘트", example = "사용 목적에 따라 리소스를 할당함", nullable = true)
        String comment,
        @Schema(description = "포트 매핑 목록")
        List<PortMappingDTO> portMappings,
        @Schema(description = "신청 생성 일시", example = "2026-03-02T15:36:29")
        LocalDateTime createdAt,
        @Schema(description = "신청 수정 일시", example = "2026-03-02T15:36:29")
        LocalDateTime updatedAt
) {
    @Builder
    @Schema(description = "리소스 그룹 정보")
    public record AdminResourceGroupInfo(
            @Schema(description = "리소스 그룹 ID", example = "1")
            Integer rsgroupId,
            @Schema(description = "리소스 그룹명", example = "RTX 4090 Cluster")
            String resourceGroupName,
            @Schema(description = "리소스 그룹 설명", example = "High-performance GPU cluster with RTX 4090 cards")
            String description,
            @Schema(description = "서버명", example = "LAB")
            String serverName
    ) {
        public static AdminResourceGroupInfo fromEntity(ResourceGroup resourceGroup) {
            return AdminResourceGroupInfo.builder()
                    .rsgroupId(resourceGroup.getRsgroupId())
                    .resourceGroupName(resourceGroup.getResourceGroupName())
                    .description(resourceGroup.getDescription())
                    .serverName(resourceGroup.getServerName())
                    .build();
        }
    }

    @Builder
    @Schema(description = "신청자 정보")
    public record AdminUserInfo(
            @Schema(description = "사용자 고유 ID", example = "1")
            Long userId,
            @Schema(description = "이메일 주소", example = "yukyum6@gmail.com")
            String email,
            @Schema(description = "이름", example = "이수아")
            String name,
            @Schema(description = "학번", example = "202312345")
            String studentId,
            @Schema(description = "학과", example = "컴퓨터공학과")
            String department,
            @Schema(description = "전화번호", example = "010-1234-5678")
            String phone,
            @Schema(description = "계정 활성화 여부", example = "true")
            Boolean isActive
    ) {
        public static AdminUserInfo fromEntity(User user) {
            return AdminUserInfo.builder()
                    .userId(user.getUserId())
                    .email(user.getEmail())
                    .name(user.getName())
                    .studentId(user.getStudentId())
                    .department(user.getDepartment())
                    .phone(user.getPhone())
                    .isActive(user.getIsActive())
                    .build();
        }
    }

    public static SaveRequestResponseDTO fromEntity(Request request) {

        if (request.getResourceGroup() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_GROUP_NOT_FOUND);
        }
        if (request.getUser() == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (request.getContainerImage() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        return SaveRequestResponseDTO.builder()
                .requestId(request.getRequestId())
                .resourceGroupId(request.getResourceGroup().getRsgroupId())
                .resourceGroup(AdminResourceGroupInfo.fromEntity(request.getResourceGroup()))
                .user(AdminUserInfo.fromEntity(request.getUser()))
                .imageId(request.getContainerImage().getImageId())
                .imageName(request.getContainerImage().getImageName())
                .imageVersion(request.getContainerImage().getImageVersion())
                .ubuntuUsername(request.getUbuntuUsername())
                .ubuntuUid(request.getUbuntuUid() != null
                        ? request.getUbuntuUid().getIdValue()
                        : null)
                .ubuntuGids(
                        request.getRequestGroups().stream()
                                .map(rg -> rg.getGroup().getUbuntuGid())
                                .toList()
                )
                .volumeSizeGiB(request.getVolumeSizeGiB())
                .usagePurpose(request.getUsagePurpose())
                .formAnswers(request.getFormAnswers())
                .expiresAt(request.getExpiresAt())
                .status(request.getStatus())
                .approvedAt(request.getApprovedAt())
                .comment(request.getAdminComment())
                .portMappings(List.of())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }

    public static SaveRequestResponseDTO fromEntityWithPortMappings(Request request, List<PortMappingDTO> portMappings) {
        if (request.getResourceGroup() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_GROUP_NOT_FOUND);
        }
        if (request.getUser() == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (request.getContainerImage() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        return SaveRequestResponseDTO.builder()
                .requestId(request.getRequestId())
                .resourceGroupId(request.getResourceGroup().getRsgroupId())
                .resourceGroup(AdminResourceGroupInfo.fromEntity(request.getResourceGroup()))
                .user(AdminUserInfo.fromEntity(request.getUser()))
                .imageId(request.getContainerImage().getImageId())
                .imageName(request.getContainerImage().getImageName())
                .imageVersion(request.getContainerImage().getImageVersion())
                .ubuntuUsername(request.getUbuntuUsername())
                .ubuntuUid(request.getUbuntuUid() != null
                        ? request.getUbuntuUid().getIdValue()
                        : null)
                .ubuntuGids(
                        request.getRequestGroups().stream()
                                .map(rg -> rg.getGroup().getUbuntuGid())
                                .toList()
                )
                .volumeSizeGiB(request.getVolumeSizeGiB())
                .usagePurpose(request.getUsagePurpose())
                .formAnswers(request.getFormAnswers())
                .expiresAt(request.getExpiresAt())
                .status(request.getStatus())
                .approvedAt(request.getApprovedAt())
                .comment(request.getAdminComment())
                .portMappings(portMappings)
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }
}