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

@Schema(description = "서버 사용 신청 상세 응답 DTO")
@Builder
public record SaveRequestResponseDTO(
        @Schema(description = "신청 ID") Long requestId,
        @Schema(description = "리소스 그룹 ID") Integer resourceGroupId,
        @Schema(description = "리소스 그룹 정보") AdminResourceGroupInfo resourceGroup,
        @Schema(description = "신청자 정보") AdminUserInfo user,
        @Schema(description = "이미지 ID") Long imageId,
        @Schema(description = "이미지 이름") String imageName,
        @Schema(description = "이미지 버전") String imageVersion,
        @Schema(description = "우분투 계정명") String ubuntuUsername,
        @Schema(description = "우분투 UID") Long ubuntuUid,
        @Schema(description = "우분투 GID 목록") List<Long> ubuntuGids,
        @Schema(description = "볼륨 크기 (GiB)") Long volumeSizeGiB,
        @Schema(description = "사용 목적") String usagePurpose,
        @Schema(description = "추가 설문 응답 (JSON)") @JsonRawValue String formAnswers,
        @Schema(description = "만료 일시") LocalDateTime expiresAt,
        @Schema(description = "신청 상태 (PENDING: 대기, FULFILLED: 승인, REJECTED: 거절)") Status status,
        @Schema(description = "승인 일시") LocalDateTime approvedAt,
        @Schema(description = "관리자 코멘트") String comment,
        @Schema(description = "포트 매핑 목록") List<PortMappingDTO> portMappings,
        @Schema(description = "생성 일시") LocalDateTime createdAt,
        @Schema(description = "수정 일시") LocalDateTime updatedAt
) {
    @Schema(description = "리소스 그룹 요약 정보")
    @Builder
    public record AdminResourceGroupInfo(
            @Schema(description = "리소스 그룹 ID") Integer rsgroupId,
            @Schema(description = "리소스 그룹 이름") String resourceGroupName,
            @Schema(description = "리소스 그룹 설명") String description,
            @Schema(description = "서버 이름") String serverName
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

    @Schema(description = "신청자 요약 정보")
    @Builder
    public record AdminUserInfo(
            @Schema(description = "사용자 ID") Long userId,
            @Schema(description = "이메일") String email,
            @Schema(description = "실명") String name,
            @Schema(description = "학번") String studentId,
            @Schema(description = "학과") String department,
            @Schema(description = "연락처") String phone,
            @Schema(description = "계정 활성화 여부") Boolean isActive
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