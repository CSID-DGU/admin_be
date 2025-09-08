package DGU_AI_LAB.admin_be.domain.requests.entity;

public enum ChangeType {
    VOLUME_SIZE, // 볼륨 사이즈 변경
    EXPIRES_AT, // 서버 사용 만료 기한 변경
    GROUP, // 사용자가 속한 그룹 변경
    RESOURCE_GROUP, // 리소스 그룹 변경
    CONTAINER_IMAGE // 도커 이미지 변경
}
