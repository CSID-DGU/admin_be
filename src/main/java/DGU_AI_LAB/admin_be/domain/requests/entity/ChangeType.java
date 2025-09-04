package DGU_AI_LAB.admin_be.domain.requests.entity;

public enum ChangeType {
    VOLUME_SIZE,
    EXPIRES_AT,
    GROUP, // 사용자가 속한 그룹 변경
    RESOURCE_GROUP, // 리소스 그룹 변경
    CONTAINER_IMAGE // 도커 이미지 변경
}
