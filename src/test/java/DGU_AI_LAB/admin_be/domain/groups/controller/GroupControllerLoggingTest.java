package DGU_AI_LAB.admin_be.domain.groups.controller;

import DGU_AI_LAB.admin_be.domain.groups.dto.request.CreateGroupRequestDTO;
import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import DGU_AI_LAB.admin_be.support.LogCaptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

/**
 * 그룹 생성 요청 로그에 ubuntuUsername(리눅스 계정명)이 남지 않는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class GroupControllerLoggingTest {

    private static final String UBUNTU_USERNAME = "sohyeon-lab-account";

    @InjectMocks
    private GroupController groupController;

    @Mock
    private GroupService groupService;

    @Test
    @DisplayName("그룹 생성 요청 로그에 ubuntuUsername이 남지 않고 groupName만 남는다")
    void doesNotLogUbuntuUsername() {
        CreateGroupRequestDTO dto = new CreateGroupRequestDTO("ai-lab-team", UBUNTU_USERNAME);

        User user = User.builder()
                .email("admin@dgu.ac.kr")
                .password("encoded")
                .name("관리자")
                .studentId("2020001234")
                .phone("010-1111-2222")
                .department("컴퓨터공학과")
                .build();
        CustomUserDetails principal = new CustomUserDetails(user, null);

        when(groupService.createGroup(any(CreateGroupRequestDTO.class), nullable(Long.class)))
                .thenReturn(new GroupResponseDTO(5000L, "ai-lab-team"));

        try (LogCaptor logCaptor = LogCaptor.forClass(GroupController.class)) {
            groupController.createGroup(dto, principal);

            String logs = logCaptor.joined();
            assertThat(logs).doesNotContain(UBUNTU_USERNAME);
            assertThat(logs).contains("ai-lab-team");
        }
    }
}
