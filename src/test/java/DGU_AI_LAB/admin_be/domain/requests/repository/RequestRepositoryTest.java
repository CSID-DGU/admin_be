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
                .ubuntuPasswordBase64("base64Pw1")
                .volumeSizeGiB(50L)
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
                .ubuntuPasswordBase64("base64Pw2")
                .volumeSizeGiB(100L)
                .expiresAt(LocalDateTime.now().plusDays(60))
                .usagePurpose("머신러닝")
                .formAnswers("{}")
                .user(user)
                .resourceGroup(resourceGroup)
                .containerImage(containerImage)
                .build();
        req2.approve(containerImage, resourceGroup, 100L, null);
        fulfilledRequest = requestRepository.save(req2);
    }

    @Nested
    @DisplayName("findAllByUser")
    class FindAllByUser {

        @Test
        @DisplayName("특정 유저의 모든 요청을 조회한다")
        void findAllByUser_returnsUserRequests() {
            List<Request> result = requestRepository.findAllByUser(user);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("findByUbuntuUsername")
    class FindByUbuntuUsername {

        @Test
        @DisplayName("존재하는 ubuntuUsername으로 조회하면 Request를 반환한다")
        void findByUbuntuUsername_returnsRequest_whenExists() {
            Optional<Request> result = requestRepository.findByUbuntuUsername("pendinguser");

            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(Status.PENDING);
        }

        @Test
        @DisplayName("존재하지 않는 ubuntuUsername으로 조회하면 빈 Optional을 반환한다")
        void findByUbuntuUsername_returnsEmpty_whenNotExists() {
            Optional<Request> result = requestRepository.findByUbuntuUsername("notexist");

            assertThat(result).isEmpty();
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
    @DisplayName("existsByUbuntuUsername")
    class ExistsByUbuntuUsername {

        @Test
        @DisplayName("존재하는 ubuntuUsername에 대해 true를 반환한다")
        void existsByUbuntuUsername_returnsTrue_whenExists() {
            assertThat(requestRepository.existsByUbuntuUsername("pendinguser")).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 ubuntuUsername에 대해 false를 반환한다")
        void existsByUbuntuUsername_returnsFalse_whenNotExists() {
            assertThat(requestRepository.existsByUbuntuUsername("notexist")).isFalse();
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

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("findAllWithUserByExpiredDateBefore (JPQL 파라미터 바인딩)")
    class FindAllWithUserByExpiredDateBefore {

        @Test
        @DisplayName("만료된 FULFILLED 요청이 반환된다")
        void findAllWithUserByExpiredDateBefore_returnsFulfilledExpired() {
            // FULFILLED 상태 요청의 expiresAt을 과거로 설정
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
            // PENDING 요청의 expiresAt을 과거로 설정
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
            // fulfilledRequest의 expiresAt은 미래로 setUp에서 설정됨

            List<Request> result = requestRepository.findAllWithUserByExpiredDateBefore(
                    LocalDateTime.now(), Status.FULFILLED
            );

            assertThat(result).isEmpty();
        }
    }
}
