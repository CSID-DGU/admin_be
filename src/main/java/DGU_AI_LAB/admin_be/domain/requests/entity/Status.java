package DGU_AI_LAB.admin_be.domain.requests.entity;

import java.util.List;

public enum Status {
    PENDING, PROCESSING, DENIED, FULFILLED, MIGRATING, REBOOTING, DELETED;

    /**
     * 실제 인프라(Pod/우분투 계정)가 살아있는 상태 집합.
     * "내 서버" 조회, 리소스 사용량 집계 등 FULFILLED를 기준으로 하던 조회 로직은
     * 마이그레이션/재시작 중에도 자원이 계속 점유돼 있으므로 이 집합을 사용해야 한다.
     * 특히 재시작은 사용자가 진행 상태를 "내 승인 완료 신청" 조회로 폴링하므로,
     * REBOOTING이 빠지면 재시작 도중 자기 컨테이너가 목록에서 사라진다.
     */
    public static List<Status> activeStatuses() {
        return List.of(FULFILLED, MIGRATING, REBOOTING);
    }
}
