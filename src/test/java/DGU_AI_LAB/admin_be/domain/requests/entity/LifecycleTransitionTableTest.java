package DGU_AI_LAB.admin_be.domain.requests.entity;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 전이 표가 한 곳(Status)에만 있고, 공개 메서드가 그 표를 벗어나지 못하는지 확인한다.
 */
class LifecycleTransitionTableTest {

    @Test
    @DisplayName("lifecycle-transitions.yaml은 Status의 전이 표와 같다 — E2E 도구가 읽는 사본이 어긋나면 안 된다")
    void yamlCopyMatchesEnumTable() throws IOException {
        Map<Status, Set<Status>> fromYaml = new EnumMap<>(Status.class);
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("lifecycle-transitions.yaml")) {
            assertThat(in).isNotNull();
            Map<String, List<String>> raw = new Yaml().load(in);
            raw.forEach((from, targets) -> {
                Set<Status> set = EnumSet.noneOf(Status.class);
                targets.forEach(t -> set.add(Status.valueOf(t)));
                fromYaml.put(Status.valueOf(from), set);
            });
        }

        Map<Status, Set<Status>> fromEnum = new EnumMap<>(Status.class);
        for (Status s : Status.values()) {
            fromEnum.put(s, s.allowedTargets());
        }
        assertThat(fromYaml).isEqualTo(fromEnum);
    }

    @Test
    @DisplayName("열린 상태(openStatuses)에서는 모두 끝(DENIED·DELETED)에 닿을 수 있다 — 갇히는 상태가 없다")
    void everyOpenStatusCanReachAnEnd() {
        for (Status start : Status.openStatuses()) {
            Set<Status> seen = EnumSet.of(start);
            List<Status> frontier = new java.util.ArrayList<>(List.of(start));
            while (!frontier.isEmpty()) {
                Status s = frontier.remove(0);
                for (Status t : s.allowedTargets()) {
                    if (seen.add(t)) {
                        frontier.add(t);
                    }
                }
            }
            assertThat(seen).as("%s에서 닿는 상태", start).containsAnyOf(Status.DENIED, Status.DELETED);
        }
    }

    @ParameterizedTest
    @EnumSource(Status.class)
    @DisplayName("각 상태에서 표에 없는 전이를 부르면 상태가 바뀌지 않고 거부된다")
    void operationsOutsideTableAreRejected(Status from) {
        Map<Status, List<Consumer<Request>>> operationsInto = Map.of(
                Status.PROCESSING, List.of(Request::markAsProcessing),
                Status.PENDING, List.of(Request::revertToPending),
                Status.FULFILLED, List.of(Request::completeApproval, Request::endMigration, Request::endExpiry),
                Status.DENIED, List.of(r -> r.reject("거절")),
                Status.MIGRATING, List.of(Request::beginMigration),
                Status.EXPIRING, List.of(Request::beginExpiry),
                Status.DELETED, List.of(Request::delete, Request::deleteAfterCleanup));

        operationsInto.forEach((target, ops) -> {
            if (from.canTransitionTo(target)) {
                return;
            }
            for (Consumer<Request> op : ops) {
                Request request = requestIn(from);
                assertThatThrownBy(() -> op.accept(request))
                        .as("%s → %s", from, target)
                        .isInstanceOf(BusinessException.class);
                assertThat(request.getStatus()).isEqualTo(from);
            }
        });
    }

    /** 공개 메서드만 써서 원하는 상태의 신청을 만든다 — 표에 있는 경로로만 닿을 수 있어야 한다. */
    private Request requestIn(Status target) {
        Request r = Request.builder()
                .expiresAt(LocalDateTime.now().plusDays(30))
                .usagePurpose("시험")
                .formAnswers("{}")
                .user(mock(User.class))
                .resourceGroup(mock(ResourceGroup.class))
                .containerImage(mock(ContainerImage.class))
                .build();
        switch (target) {
            case PENDING -> { }
            case PROCESSING -> r.markAsProcessing();
            case DENIED -> r.reject("거절");
            case FULFILLED -> fulfill(r);
            case MIGRATING -> { fulfill(r); r.beginMigration(); }
            case EXPIRING -> { fulfill(r); r.beginExpiry(); }
            case DELETED -> r.delete();
        }
        assertThat(r.getStatus()).isEqualTo(target);
        return r;
    }

    private void fulfill(Request r) {
        r.markAsProcessing();
        r.completeApproval();
    }
}
