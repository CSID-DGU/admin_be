package DGU_AI_LAB.admin_be.domain.requests.entity;

import java.util.List;

public enum Status {
    PENDING, PROCESSING, DENIED, FULFILLED, MIGRATING, EXPIRING, DELETED;

    /**
     * 실제 인프라(Pod/우분투 계정)가 살아있는 상태 집합.
     * "내 서버" 조회, 리소스 사용량 집계 등 FULFILLED를 기준으로 하던 조회 로직은
     * 마이그레이션 중에도 자원이 계속 점유돼 있으므로 이 집합을 사용해야 한다.
     * EXPIRING도 같은 이유로 포함한다 — 정리가 끝나 DELETED로 넘어가기 전까지는
     * Pod/계정이 아직 남아있어 자원을 점유한다.
     */
    public static List<Status> activeStatuses() {
        return List.of(FULFILLED, MIGRATING, EXPIRING);
    }

    /**
     * 아직 끝나지 않은(DENIED/DELETED가 아닌) 신청 상태 집합.
     * 유저네임이 웹 계정 단위로 고정되면서 Request.ubuntuUsername은 더 이상 유일하지 않다 —
     * 같은 유저네임을 가진 과거 이력 행이 계속 쌓이므로, 유저네임으로 "지금 살아있는 신청"을
     * 찾는 조회(config-server의 accept-info 등)는 이 집합으로 범위를 좁혀야 한다.
     */
    public static List<Status> openStatuses() {
        return List.of(PENDING, PROCESSING, FULFILLED, MIGRATING, EXPIRING);
    }
}
