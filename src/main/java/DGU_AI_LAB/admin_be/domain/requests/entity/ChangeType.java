package DGU_AI_LAB.admin_be.domain.requests.entity;

/** 변경 요청 종류. label은 사용자에게 보내는 안내 메일에 쓰는 이름이다. */
public enum ChangeType {
    EXPIRES_AT("사용 기간 연장"),
    RESOURCE_GROUP("GPU 변경"),
    CONTAINER_IMAGE("개발 환경 변경"),
    GROUP("공유 그룹 추가"),
    PORT("추가 포트 변경");

    private final String label;

    ChangeType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
