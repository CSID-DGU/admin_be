package DGU_AI_LAB.admin_be.domain.portRequests.service;

import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortRequestServiceTest {

    @InjectMocks
    private PortRequestService portRequestService;

    @Mock
    private PortRequestRepository portRequestRepository;

    @Test
    @DisplayName("NodePort 기본 범위 30000부터 사용 가능한 포트를 할당한다")
    void createPortRequest_assignsNodePortFromDefaultRange() {
        ResourceGroup resourceGroup = ResourceGroup.builder()
                .rsgroupId(1)
                .build();
        Request request = Request.builder()
                .ubuntuUsername("testuser")
                .ubuntuPassword("password")
                .ubuntuPasswordBase64("cGFzc3dvcmQ=")
                .volumeSizeGiB(10L)
                .usagePurpose("학습")
                .formAnswers("{}")
                .resourceGroup(resourceGroup)
                .build();

        when(portRequestRepository.findPortNumbersByResourceGroupRsgroupIdOrderByPortNumberAsc(1))
                .thenReturn(List.of(30000));
        when(portRequestRepository.save(any(PortRequests.class))).thenAnswer(invocation -> invocation.getArgument(0));

        portRequestService.createPortRequest(request, resourceGroup, 8888, "jupyter");

        ArgumentCaptor<PortRequests> captor = ArgumentCaptor.forClass(PortRequests.class);
        verify(portRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getPortNumber()).isEqualTo(30001);
    }

    @Test
    @DisplayName("30000-32767 범위가 모두 사용 중이면 BusinessException을 던진다")
    void createPortRequest_throwsException_whenNoNodePortAvailable() {
        ResourceGroup resourceGroup = ResourceGroup.builder()
                .rsgroupId(1)
                .build();
        Request request = Request.builder()
                .ubuntuUsername("testuser")
                .ubuntuPassword("password")
                .ubuntuPasswordBase64("cGFzc3dvcmQ=")
                .volumeSizeGiB(10L)
                .usagePurpose("학습")
                .formAnswers("{}")
                .resourceGroup(resourceGroup)
                .build();
        List<Integer> usedPorts = IntStream.rangeClosed(30000, 32767)
                .boxed()
                .toList();

        when(portRequestRepository.findPortNumbersByResourceGroupRsgroupIdOrderByPortNumberAsc(1))
                .thenReturn(usedPorts);

        assertThatThrownBy(() -> portRequestService.createPortRequest(request, resourceGroup, 8888, "jupyter"))
                .isInstanceOf(BusinessException.class);
    }
}
