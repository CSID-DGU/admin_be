package DGU_AI_LAB.admin_be.domain.groups.controller;

import DGU_AI_LAB.admin_be.domain.groups.dto.request.CreateGroupRequestDTO;
import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupOperationService;
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
 * 그룹 생성 요청 로그에 사용자 식별 정보가 남지 않고 그룹명만 남는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class GroupControllerLoggingTest {

    private static final String EMAIL = "admin@dgu.ac.kr";

    @InjectMocks
    private GroupController groupController;

    @Mock
    private GroupService groupService;

    @Mock
    private GroupOperationService groupOperationService;

    @Test
    @DisplayName("그룹 생성 요청 로그에 사용자 이메일이 남지 않고 groupName만 남는다")
    void logsOnlyGroupName() {
        CreateGroupRequestDTO dto = new CreateGroupRequestDTO("ai-lab-team");

        User user = User.builder()
                .email(EMAIL)
                .password("encoded")
                .name("관리자")
                .studentId("2020001234")
                .phone("010-1111-2222")
                .department("컴퓨터공학과")
                .build();
        CustomUserDetails principal = new CustomUserDetails(user, null);

        when(groupService.createGroup(any(CreateGroupRequestDTO.class), nullable(Long.class)))
                .thenReturn(new GroupResponseDTO(7L, null, "ai-lab-team"));

        try (LogCaptor logCaptor = LogCaptor.forClass(GroupController.class)) {
            var response = groupController.createGroup(dto, principal);

            // 그룹은 DB 에 바로 생기므로 작업 등록(202)이 아니라 생성(201)으로 돌려준다.
            assertThat(response.getStatusCode().value()).isEqualTo(201);

            String logs = logCaptor.joined();
            assertThat(logs).doesNotContain(EMAIL);
            assertThat(logs).contains("ai-lab-team");
        }
    }
}
