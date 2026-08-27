package DGU_AI_LAB.admin_be.domain.requests.entity;

import java.util.List;

public enum Status {
    PENDING, PROCESSING, DENIED, FULFILLED, MIGRATING, DELETED;

    /**
     * 실제 인프라(Pod/우분투 계정)가 살아있는 상태 집합.
     * "내 서버" 조회, 리소스 사용량 집계 등 FULFILLED를 기준으로 하던 조회 로직은
     * 마이그레이션 중에도 자원이 계속 점유돼 있으므로 이 집합을 사용해야 한다.
     */
    public static List<Status> activeStatuses() {
        return List.of(FULFILLED, MIGRATING);
    }
}
