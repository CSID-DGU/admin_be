package DGU_AI_LAB.admin_be.domain.requests.entity;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RequestNumberLedgerTest {

    @Autowired private RequestRepository requests;
    @Autowired private UserRepository users;
    @Autowired private ResourceGroupRepository groups;
    @Autowired private ContainerImageRepository images;

    private Request newRequest(boolean test) {
        User user = users.findAll().stream().findFirst().orElseGet(() -> users.save(User.builder()
                .email("ledger@dgu.ac.kr").password("p").name("대장").studentId("2021007777")
                .phone("010-7777-0000").department("컴퓨터공학과").build()));
        ResourceGroup rg = groups.save(ResourceGroup.builder()
                .resourceGroupName("g" + System.nanoTime()).description("d").serverName("FARM").build());
        ContainerImage img = images.save(ContainerImage.builder()
                .imageName("i" + System.nanoTime()).imageVersion("1").cudaVersion("12").description("d").build());
        Request r = Request.builder().expiresAt(LocalDateTime.now().plusDays(1)).usagePurpose("p")
                .formAnswers("{}").user(user).resourceGroup(rg).containerImage(img).build();
        if (test) {
            r.markAsTest("farm1");
        }
        return requests.saveAndFlush(r);
    }

    @Test
    void 테스트_신청은_실_사용자_번호를_쓰지_않는다() {
        long first = newRequest(false).getRequestId();
        long testNo = newRequest(true).getRequestId();
        long second = newRequest(false).getRequestId();

        assertThat(testNo).isGreaterThanOrEqualTo(2_000_000_001L).isLessThanOrEqualTo(Integer.MAX_VALUE);
        assertThat(second).isEqualTo(first + 1);
    }
}
