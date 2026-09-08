package DGU_AI_LAB.admin_be.domain.requests.repository;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RequestRepositoryTest {

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResourceGroupRepository resourceGroupRepository;

    @Autowired
    private ContainerImageRepository containerImageRepository;

    private User user;
    private ResourceGroup resourceGroup;
    private ContainerImage containerImage;
    private Request pendingRequest;
    private Request fulfilledRequest;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("test@dgu.ac.kr")
                .password("encoded")
                .name("홍길동")
                .studentId("2021001234")
                .phone("010-1111-2222")
                .department("컴퓨터공학과")
                .ubuntuUsername("pendinguser")
                .build());

        resourceGroup = resourceGroupRepository.save(ResourceGroup.builder()
                .resourceGroupName("GPU Server A")
                .description("메인 GPU 서버")
                .serverName("server-01")
                .build());

        containerImage = containerImageRepository.save(ContainerImage.builder()
                .imageName("pytorch")
                .imageVersion("2.1.0")
                .cudaVersion("11.8")
                .description("PyTorch 2.1.0")
                .build());

        pendingRequest = requestRepository.save(Request.builder()
                .ubuntuUsername("pendinguser")
                .ubuntuPassword("hashedPw1")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .usagePurpose("연구 목적")
                .formAnswers("{}")
                .user(user)
                .resourceGroup(resourceGroup)
                .containerImage(containerImage)
                .build());

        Request req2 = Request.builder()
                .ubuntuUsername("fulfilleduser")
                .ubuntuPassword("hashedPw2")
                .expiresAt(LocalDateTime.now().plusDays(60))
                .usagePurpose("머신러닝")
                .formAnswers("{}")
                .user(user)
                .resourceGroup(resourceGroup)
                .containerImage(containerImage)
                .build();
        req2.approve(containerImage, resourceGroup, null);
        fulfilledRequest = requestRepository.save(req2);

        // 같은 유저네임을 쓰는 종료된 이력 — 유저네임이 웹 계정 단위로 고정되면서
        // Request.ubuntuUsername의 unique 제약이 사라졌음을 함께 검증한다.
        Request denied = Request.builder()
                .ubuntuUsername("pendinguser")
                .ubuntuPassword("hashedPw3")
                .expiresAt(LocalDateTime.now().plusDays(10))
                .usagePurpose("지난 신청")
                .formAnswers("{}")
                .user(user)
                .resourceGroup(resourceGroup)
                .containerImage(containerImage)
                .build();
        denied.reject("리소스 부족");
        requestRepository.save(denied);
    }

    @Nested
    @DisplayName("findAllByUser")
    class FindAllByUser {

        @Test
        @DisplayName("특정 유저의 모든 요청을 조회한다")
        void findAllByUser_returnsUserRequests() {
            List<Request> result = requestRepository.findAllByUser(user);

            assertThat(result).hasSize(3);
        }
    }

    @Nested
    @DisplayName("findByUbuntuUsernameAndStatusInOrderByRequestIdDesc")
    class FindByUbuntuUsernameAndStatusIn {

        @Test
        @DisplayName("살아있는 상태의 Request만 반환한다")
        void returnsOpenRequest_whenExists() {
            List<Request> result = requestRepository
                    .findByUbuntuUsernameAndStatusInOrderByRequestIdDesc("pendinguser", Status.openStatuses());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(Status.PENDING);
        }

        @Test
        @DisplayName("존재하지 않는 ubuntuUsername으로 조회하면 빈 목록을 반환한다")
        void returnsEmpty_whenNotExists() {
            List<Request> result = requestRepository
                    .findByUbuntuUsernameAndStatusInOrderByRequestIdDesc("notexist", Status.openStatuses());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("같은 유저네임의 종료된(DENIED) 이력은 제외한다 — 유저네임은 더 이상 유일하지 않다")
        void excludesClosedHistory() {
            List<Request> all = requestRepository.findAllByUser(user);
            assertThat(all).filteredOn(r -> "pendinguser".equals(r.getUbuntuUsername())).hasSize(2);

            List<Request> result = requestRepository
                    .findByUbuntuUsernameAndStatusInOrderByRequestIdDesc("pendinguser", Status.openStatuses());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(Status.PENDING);
        }
    }

    @Nested
    @DisplayName("findAllByStatus")
    class FindAllByStatus {

        @Test
        @DisplayName("PENDING 상태 요청 목록을 조회한다")
        void findAllByStatus_returnsPendingRequests() {
            List<Request> result = requestRepository.findAllByStatus(Status.PENDING);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUbuntuUsername()).isEqualTo("pendinguser");
        }

        @Test
        @DisplayName("FULFILLED 상태 요청 목록을 조회한다")
        void findAllByStatus_returnsFulfilledRequests() {
            List<Request> result = requestRepository.findAllByStatus(Status.FULFILLED);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUbuntuUsername()).isEqualTo("fulfilleduser");
        }
    }

    @Nested
    @DisplayName("existsByUser_UserIdAndStatusIn")
    class ExistsByUserAndStatusIn {

        @Test
        @DisplayName("살아있는 신청을 가진 유저에 대해 true를 반환한다")
        void returnsTrue_whenUserHasOpenRequest() {
            assertThat(requestRepository.existsByUser_UserIdAndStatusIn(user.getUserId(), Status.openStatuses())).isTrue();
        }

        @Test
        @DisplayName("살아있는 신청이 없는 유저에 대해 false를 반환한다")
        void returnsFalse_whenUserHasNoOpenRequest() {
            assertThat(requestRepository.existsByUser_UserIdAndStatusIn(user.getUserId(), List.of(Status.MIGRATING))).isFalse();
        }
    }

    @Nested
    @DisplayName("findUbuntuUsernamesByStatus")
    class FindUbuntuUsernamesByStatus {

        @Test
        @DisplayName("FULFILLED 상태 요청들의 ubuntuUsername 목록을 반환한다")
        void findUbuntuUsernamesByStatus_returnsFulfilledUsernames() {
            List<String> result = requestRepository.findUbuntuUsernamesByStatus(Status.FULFILLED);

            assertThat(result).containsExactly("fulfilleduser");
        }
    }

    @Nested
    @DisplayName("findAllByUser_UserId")
    class FindAllByUserUserId {

        @Test
        @DisplayName("userId로 해당 유저의 모든 요청을 조회한다")
        void findAllByUserUserId_returnsRequests() {
            List<Request> result = requestRepository.findAllByUser_UserId(user.getUserId());

            assertThat(result).hasSize(3);
        }
    }

    @Nested
    @DisplayName("findAllWithUserByExpiredDateBefore (JPQL 파라미터 바인딩)")
    class FindAllWithUserByExpiredDateBefore {

        @Test
        @DisplayName("만료된 FULFILLED 요청이 반환된다")
        void findAllWithUserByExpiredDateBefore_returnsFulfilledExpired() {
            fulfilledRequest.updateExpiresAt(LocalDateTime.now().minusDays(1));
            requestRepository.save(fulfilledRequest);

            List<Request> result = requestRepository.findAllWithUserByExpiredDateBefore(
                    LocalDateTime.now(), Status.FULFILLED
            );

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(Status.FULFILLED);
        }

        @Test
        @DisplayName("PENDING 상태 요청은 만료 대상에 포함되지 않는다")
        void findAllWithUserByExpiredDateBefore_excludesPendingRequests() {
            pendingRequest.updateExpiresAt(LocalDateTime.now().minusDays(1));
            requestRepository.save(pendingRequest);

            List<Request> result = requestRepository.findAllWithUserByExpiredDateBefore(
                    LocalDateTime.now(), Status.FULFILLED
            );

            assertThat(result).noneMatch(r -> r.getStatus() == Status.PENDING);
        }

        @Test
        @DisplayName("미래 만료 FULFILLED 요청은 반환되지 않는다")
        void findAllWithUserByExpiredDateBefore_excludesFutureExpiry() {
            List<Request> result = requestRepository.findAllWithUserByExpiredDateBefore(
                    LocalDateTime.now(), Status.FULFILLED
            );

            assertThat(result).isEmpty();
        }
    }
}
