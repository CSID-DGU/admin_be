package DGU_AI_LAB.admin_be.domain.requests.entity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public enum Status {
    PENDING, PROCESSING, DENIED, FULFILLED, MIGRATING, EXPIRING, DELETED;

    /**
     * 신청 생명주기의 전이 표 — 상태 변경은 이 표에 있는 것만 허용한다(Request.transitionTo).
     * src/main/resources/lifecycle-transitions.yaml이 같은 표의 사본이며, E2E 도구가 그 파일로
     * 모든 전이를 시험했는지 확인한다. 둘이 어긋나면 LifecycleTransitionTableTest가 실패한다.
     *
     * <pre>
     * PENDING    → PROCESSING(승인 시작) · DENIED(거절) · DELETED(취소)
     * PROCESSING → FULFILLED(생성 작업 성공) · PENDING(작업 실패·방치 복구) · DENIED(작업 중 거절)
     * DENIED     → DELETED(취소)
     * FULFILLED  → MIGRATING(노드 이동 시작) · EXPIRING(회수 시작)
     * MIGRATING  → FULFILLED(이동 끝)
     * EXPIRING   → FULFILLED(회수 실패로 되돌림) · DELETED(회수 끝)
     * DELETED    → (끝)
     * </pre>
     */
    private static final Map<Status, Set<Status>> TRANSITIONS = new EnumMap<>(Status.class);

    static {
        TRANSITIONS.put(PENDING, EnumSet.of(PROCESSING, DENIED, DELETED));
        TRANSITIONS.put(PROCESSING, EnumSet.of(FULFILLED, PENDING, DENIED));
        TRANSITIONS.put(DENIED, EnumSet.of(DELETED));
        TRANSITIONS.put(FULFILLED, EnumSet.of(MIGRATING, EXPIRING));
        TRANSITIONS.put(MIGRATING, EnumSet.of(FULFILLED));
        TRANSITIONS.put(EXPIRING, EnumSet.of(FULFILLED, DELETED));
        TRANSITIONS.put(DELETED, EnumSet.noneOf(Status.class));
    }

    public boolean canTransitionTo(Status target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** 이 상태에서 갈 수 있는 상태들. 표를 바깥(테스트·문서)에 보여 줄 때만 쓴다. */
    public Set<Status> allowedTargets() {
        return EnumSet.copyOf(TRANSITIONS.get(this));
    }

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
     * 유저네임은 웹 계정(User)에 하나라 같은 사용자의 과거 신청 행이 계속 쌓이므로, 유저네임으로 "지금 살아있는 신청"을
     * 찾는 조회(config-server의 accept-info 등)는 이 집합으로 범위를 좁혀야 한다.
     */
    public static List<Status> openStatuses() {
        return List.of(PENDING, PROCESSING, FULFILLED, MIGRATING, EXPIRING);
    }
}
