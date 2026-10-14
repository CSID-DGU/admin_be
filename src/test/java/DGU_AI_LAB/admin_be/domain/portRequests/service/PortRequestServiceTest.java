package DGU_AI_LAB.admin_be.domain.portRequests.service;

import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("createPortRequest는 internalPort와 usagePurpose를 저장한다")
    void createPortRequest_savesInternalPortAndPurpose() {
        ResourceGroup resourceGroup = ResourceGroup.builder()
                .rsgroupId(1)
                .build();
        Request request = Request.builder()
                .usagePurpose("학습")
                .formAnswers("{}")
                .resourceGroup(resourceGroup)
                .build();

        when(portRequestRepository.save(any(PortRequests.class))).thenAnswer(invocation -> invocation.getArgument(0));

        portRequestService.createPortRequest(request, resourceGroup, 8888, "jupyter");

        ArgumentCaptor<PortRequests> captor = ArgumentCaptor.forClass(PortRequests.class);
        verify(portRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getInternalPort()).isEqualTo(8888);
        assertThat(captor.getValue().getUsagePurpose()).isEqualTo("jupyter");
    }
}
