package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.entity.UserGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClusterGroupResponseBoundaryTest {

    @Test
    @DisplayName("LAB accept-info는 FARM 계정과 신청에 남은 그룹을 config-server에 노출하지 않는다")
    void acceptInfoForLabHasNoGroups() {
        Request request = requestOn("LAB");
        User user = request.getUser();

        AcceptInfoResponseDTO response = AcceptInfoResponseDTO.fromEntity(
                "LAB", request, List.of(), List.of());

        assertThat(response.groups()).isEmpty();
        verify(user, never()).getUserGroups();
        verify(request, never()).getRequestGroups();
    }

    @Test
    @DisplayName("LAB 컨테이너 정보는 FARM 전역 그룹 카탈로그의 GID를 노출하지 않는다")
    void containerInfoForLabHasNoUbuntuGids() {
        Request request = requestOn("LAB");
        User user = request.getUser();

        ContainerInfoDTO response = ContainerInfoDTO.fromEntity(request, null, null);

        assertThat(response.ubuntuGids()).isEmpty();
        verify(user, never()).getUserGroups();
    }

    @Test
    @DisplayName("LAB 신청 상세는 미리 조회한 FARM 계정 그룹 GID를 노출하지 않는다")
    void saveRequestResponseForLabHasNoUbuntuGids() {
        Request request = requestOn("LAB");

        SaveRequestResponseDTO response = SaveRequestResponseDTO.fromEntityWithPorts(
                request, List.of(), List.of(), List.of(), List.of(1005L, 1006L));

        assertThat(response.ubuntuGids()).isEmpty();
        assertThat(response.resourceGroup().serverName()).isEqualTo("LAB");
    }

    @Test
    @DisplayName("FARM 응답은 기존 공용 그룹 GID를 유지한다")
    void farmResponsesKeepUbuntuGids() {
        Request request = requestOn(User.PRIMARY_SERVER);
        Group group = mock(Group.class);
        when(group.getUbuntuGid()).thenReturn(1005L);
        UserGroup membership = mock(UserGroup.class);
        when(membership.getGroup()).thenReturn(group);
        when(request.getUser().getUserGroups()).thenReturn(new LinkedHashSet<>(List.of(membership)));

        assertThat(ContainerInfoDTO.fromEntity(request, null, null).ubuntuGids()).containsExactly(1005L);
        assertThat(SaveRequestResponseDTO.fromEntityWithPorts(
                request, List.of(), List.of(), List.of(), List.of(1005L)).ubuntuGids()).containsExactly(1005L);
    }

    private static Request requestOn(String clusterId) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(1L);
        when(user.getName()).thenReturn("alice");

        ResourceGroup resourceGroup = mock(ResourceGroup.class);
        when(resourceGroup.getRsgroupId()).thenReturn(3);
        when(resourceGroup.getResourceGroupName()).thenReturn("GPU");
        // ResourceGroup은 나중에 바뀔 수 있다. 응답 경계는 Request의 고정 clusterId로 판단해야 한다.
        when(resourceGroup.getServerName()).thenReturn(User.PRIMARY_SERVER);

        ContainerImage image = mock(ContainerImage.class);
        when(image.getImageId()).thenReturn(4L);
        when(image.getImageName()).thenReturn("cuda");
        when(image.getImageVersion()).thenReturn("12.4");

        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(9L);
        when(request.getServerName()).thenReturn(clusterId);
        when(request.getUser()).thenReturn(user);
        when(request.getResourceGroup()).thenReturn(resourceGroup);
        when(request.getContainerImage()).thenReturn(image);
        when(request.getUbuntuUsername()).thenReturn("alice");
        return request;
    }
}
