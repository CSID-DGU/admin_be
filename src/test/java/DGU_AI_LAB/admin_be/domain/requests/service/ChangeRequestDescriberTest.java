package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeRequestDescriberTest {

    @Mock private GroupRepository groupRepository;

    private ChangeRequestDescriber describer;
    private User user;

    @BeforeEach
    void setUp() {
        describer = new ChangeRequestDescriber(groupRepository, new ObjectMapper());
        user = User.builder().email("hong@dgu.ac.kr").password("p").name("홍길동").build();
    }

    private ChangeRequest change(ChangeType type, Request request, String oldValue, String newValue) {
        return ChangeRequest.builder().request(request).changeType(type).oldValue(oldValue).newValue(newValue)
                .reason("사유").requestedBy(user).build();
    }

    @Test
    @DisplayName("기간 연장은 지금 만료일과 요청한 만료일, 늘어나는 일수를 적는다")
    void describesExpiry() {
        Request request = mock(Request.class);
        when(request.getExpiresAt()).thenReturn(LocalDateTime.of(2026, 10, 20, 23, 59, 59));

        String text = describer.describe(change(ChangeType.EXPIRES_AT, request, null, "\"2026-11-03T23:59:59\""));

        assertThat(text).isEqualTo("2026-10-20 → 2026-11-03 (14일 연장)");
    }

    @Test
    @DisplayName("공유 그룹 추가는 새로 더해지는 그룹과 지금 그룹을 이름으로 적는다")
    void describesAddedGroups() {
        user.addGroupIfAbsent(new Group("teamshare", 3001L));
        when(groupRepository.findAllByUbuntuGidIn(Set.of(3002L))).thenReturn(List.of(new Group("vision<lab>", 3002L)));

        String text = describer.describe(change(ChangeType.GROUP, mock(Request.class), "[3001]", "[3001,3002]"));

        // 그룹 이름은 사용자가 정한 글이라 Slack 글로 다듬어 넣는다.
        assertThat(text).isEqualTo("추가 vision&lt;lab&gt; (지금 teamshare)");
    }

    @Test
    @DisplayName("추가 포트 변경은 지금 포트와 바뀐 뒤 포트를 적고, 모두 닫으면 없음으로 적는다")
    void describesPorts() {
        String text = describer.describe(change(ChangeType.PORT, mock(Request.class),
                "[{\"internalPort\":3000,\"usagePurpose\":\"웹 서버\"}]", "[]"));

        assertThat(text).isEqualTo("3000번 (웹 서버) → 없음");
    }

    @Test
    @DisplayName("값을 읽지 못해도 접수 알림을 막지 않고 종류 이름만 적는다")
    void fallsBackToTypeLabel() {
        String text = describer.describe(change(ChangeType.PORT, mock(Request.class), "not-json", "[]"));

        assertThat(text).isEqualTo(ChangeType.PORT.label());
    }
}
