package DGU_AI_LAB.admin_be.domain.issuanceSheet;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import org.assertj.core.data.Index;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("IssuanceSheetRows")
class IssuanceSheetRowsTest {

    static Request request(long id, Status status, String name, String nodeName, String... groupNames) {
        return request(id, id, "FARM", status, name, nodeName, groupNames);
    }

    static Request request(long id, long userId, String serverName, Status status, String name, String nodeName,
                           String... groupNames) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(userId);
        when(user.getName()).thenReturn(name);
        when(user.getUbuntuUsername()).thenReturn("user" + userId);
        when(user.getUbuntuUid()).thenReturn(55000L + userId);
        when(user.getUbuntuGid()).thenReturn(56000L + userId);
        when(user.getEmail()).thenReturn("user" + userId + "@example.test");
        when(user.getPhone()).thenReturn("01000000000");

        ContainerImage image = mock(ContainerImage.class);
        when(image.getImageName()).thenReturn("dguailab/decs");
        when(image.getImageVersion()).thenReturn("260915");
        when(image.getCudaVersion()).thenReturn("12.3.0");

        ResourceGroup resourceGroup = mock(ResourceGroup.class);
        when(resourceGroup.getResourceGroupName()).thenReturn("RTX 3090");
        when(resourceGroup.getServerName()).thenReturn(serverName);

        Set<RequestGroup> requestGroups = new LinkedHashSet<>();
        for (String groupName : groupNames) {
            Group group = mock(Group.class);
            when(group.getGroupName()).thenReturn(groupName);
            RequestGroup requestGroup = mock(RequestGroup.class);
            when(requestGroup.getGroup()).thenReturn(group);
            requestGroups.add(requestGroup);
        }

        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(id);
        when(request.getStatus()).thenReturn(status);
        when(request.getUser()).thenReturn(user);
        when(request.getContainerImage()).thenReturn(image);
        when(request.getResourceGroup()).thenReturn(resourceGroup);
        when(request.getRequestGroups()).thenReturn(requestGroups);
        when(request.getNodeName()).thenReturn(nodeName);
        when(request.getPodName()).thenReturn("ailab-user" + userId + "-" + id);
        when(request.getAdminComment()).thenReturn("연구실 과제");
        when(request.getExpiresAt()).thenReturn(LocalDateTime.of(2026, 12, 31, 23, 59));
        when(request.getApprovedAt()).thenReturn(LocalDateTime.of(2026, 10, 5, 9, 0));
        return request;
    }

    @Test
    @DisplayName("신청 한 건이 머리글과 같은 열 수의 행 하나가 된다")
    void rowMatchesHeader() {
        List<List<String>> rows = IssuanceSheetRows.of(
                List.of(request(7, Status.FULFILLED, "홍길동", "farm3", "vision", "nlp")),
                Map.of(7L, List.of("ssh(30006)", "jupyter(30007)")),
                Map.of(7L, LocalDate.of(2027, 1, 30)));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).isEqualTo(IssuanceSheetRows.HEADER);
        assertThat(rows.get(1)).containsExactly(
                "7", "홍길동", "user7", "nlp, vision", "farm3", "55007", "56007", "ssh(30006), jupyter(30007)",
                "2026-12-31", "2027-01-30", "2026-10-05", "dguailab/decs:260915", "12.3.0",
                "ailab-user7-7", "user7@example.test", "01000000000", "연구실 과제", "RTX 3090");
    }

    @Test
    @DisplayName("신청 번호 순으로 놓인다")
    void orderedByRequestId() {
        List<List<String>> rows = IssuanceSheetRows.of(List.of(
                request(12, Status.FULFILLED, "나", "farm2"),
                request(3, Status.EXPIRING, "다", "farm1"),
                request(4, Status.MIGRATING, "가", "farm1")), Map.of(), Map.of());

        assertThat(rows.stream().skip(1).map(row -> row.get(0))).containsExactly("3", "4", "12");
    }

    @Test
    @DisplayName("빈 값은 빈 칸으로 적고, 내역이 없으면 머리글만 남는다")
    void nullsBecomeBlank() {
        Request request = request(9, Status.FULFILLED, "홍길동", null);
        when(request.getPodName()).thenReturn(null);
        when(request.getAdminComment()).thenReturn(null);

        assertThat(IssuanceSheetRows.of(List.of(request), Map.of(), Map.of()).get(1))
                .contains("", Index.atIndex(4))
                .contains("", Index.atIndex(7))
                .contains("", Index.atIndex(9))
                .contains("", Index.atIndex(13))
                .contains("", Index.atIndex(16));
        assertThat(IssuanceSheetRows.of(List.of(), Map.of(), Map.of())).containsExactly(IssuanceSheetRows.HEADER);
    }
}
