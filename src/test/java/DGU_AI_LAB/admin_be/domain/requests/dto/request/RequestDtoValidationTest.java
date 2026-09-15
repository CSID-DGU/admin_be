package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.groups.dto.request.CreateGroupRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.EmailVerifyRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.PasswordUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UbuntuUsernameRegisterRequestDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 요청 경계 검증. DB 컬럼 길이·리눅스 계정 규칙·쿠버네티스 이름 규칙을 넘는 값이 서비스와 DB,
 * config-server까지 내려가면 500이나 작업 실패로 끝나므로 여기서 400으로 끊는지 확인한다.
 */
class RequestDtoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static <T> Set<String> violatedPaths(T dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Nested
    @DisplayName("신청 저장")
    class SaveRequest {

        private SaveRequestRequestDTO.SaveRequestRequestDTOBuilder valid() {
            return SaveRequestRequestDTO.builder()
                    .resourceGroupId(1)
                    .imageId(1L)
                    .ubuntuPassword("strongPassword1!")
                    .usagePurpose("딥러닝 모델 학습")
                    .formAnswers(Map.of("q", "a"))
                    .expiresAt(LocalDateTime.now().plusDays(30))
                    .ubuntuGids(Set.of(20004L))
                    .portRequests(List.of(new PortRequestDTO(8080, "웹 서버")));
        }

        @Test
        @DisplayName("정상 입력은 위반이 없다")
        void valid_hasNoViolations() {
            assertThat(validator.validate(valid().build())).isEmpty();
        }

        @Test
        @DisplayName("포트 요청 목록 안쪽의 포트 범위·사용 목적도 검사한다")
        void portRequests_areValidatedInside() {
            SaveRequestRequestDTO dto = valid()
                    .portRequests(List.of(new PortRequestDTO(70000, "웹"), new PortRequestDTO(22, " ")))
                    .build();

            assertThat(violatedPaths(dto))
                    .contains("portRequests[0].internalPort", "portRequests[1].usagePurpose");
        }

        @Test
        @DisplayName("사용 목적이 컬럼 길이 1000자를 넘으면 거절한다")
        void usagePurpose_over1000_isRejected() {
            assertThat(violatedPaths(valid().usagePurpose("a".repeat(1001)).build())).contains("usagePurpose");
            assertThat(violatedPaths(valid().usagePurpose("a".repeat(1000)).build())).doesNotContain("usagePurpose");
        }

        @Test
        @DisplayName("우분투 비밀번호는 8자 이상이어야 한다")
        void ubuntuPassword_minLength() {
            assertThat(violatedPaths(valid().ubuntuPassword("short1!").build())).contains("ubuntuPassword");
        }

        @Test
        @DisplayName("그룹 GID는 양수여야 하고 20개를 넘을 수 없다")
        void ubuntuGids_bounds() {
            assertThat(violatedPaths(valid().ubuntuGids(Set.of(-1L)).build())).anyMatch(p -> p.startsWith("ubuntuGids"));
            Set<Long> many = java.util.stream.LongStream.rangeClosed(1, 21).boxed().collect(Collectors.toSet());
            assertThat(violatedPaths(valid().ubuntuGids(many).build())).contains("ubuntuGids");
        }

        @Test
        @DisplayName("ID가 양수가 아니면 거절한다")
        void ids_mustBePositive() {
            assertThat(violatedPaths(valid().resourceGroupId(0).imageId(-3L).build()))
                    .contains("resourceGroupId", "imageId");
        }
    }

    @Nested
    @DisplayName("변경 요청")
    class ModifyRequest {

        @Test
        @DisplayName("변경 항목이 하나도 없으면 거절한다 — 서비스는 아무것도 만들지 않고 성공으로 끝나기 때문")
        void noChange_isRejected() {
            ModifyRequestDTO dto = new ModifyRequestDTO("사유", null, Set.of(), null, null);

            assertThat(violatedPaths(dto)).contains("anyChangeRequested");
        }

        @Test
        @DisplayName("변경 항목이 하나라도 있으면 통과한다")
        void oneChange_isAccepted() {
            ModifyRequestDTO dto = new ModifyRequestDTO("사유", LocalDateTime.now().plusDays(10), null, null, null);

            assertThat(validator.validate(dto)).isEmpty();
        }

        @Test
        @DisplayName("과거 만료 일시와 1000자를 넘는 사유는 거절한다")
        void pastExpiryAndLongReason_areRejected() {
            ModifyRequestDTO dto = new ModifyRequestDTO("a".repeat(1001), LocalDateTime.now().minusDays(1), null, null, null);

            assertThat(violatedPaths(dto)).contains("reason", "requestedExpiresAt");
        }
    }

    @Nested
    @DisplayName("관리자 코멘트 길이 (DB 컬럼 초과 시 500 방지)")
    class AdminComments {

        @Test
        @DisplayName("신청 승인·거절 코멘트는 300자까지")
        void requestComments_300() {
            assertThat(violatedPaths(new ApproveRequestDTO(1L, 1L, 1, "a".repeat(301)))).contains("adminComment");
            assertThat(violatedPaths(new ApproveRequestDTO(1L, 1L, 1, null))).isEmpty();
            assertThat(violatedPaths(new RejectRequestDTO(1L, "a".repeat(301)))).contains("adminComment");
            assertThat(violatedPaths(new RejectRequestDTO(1L, "a".repeat(300)))).isEmpty();
        }

        @Test
        @DisplayName("변경 요청 승인·거절 코멘트는 500자까지")
        void modificationComments_500() {
            assertThat(violatedPaths(new ApproveModificationDTO(1L, "a".repeat(501)))).contains("adminComment");
            assertThat(violatedPaths(new RejectModificationDTO(1L, "a".repeat(500)))).isEmpty();
        }

        @Test
        @DisplayName("단건 변경 요청 사유는 1000자까지")
        void singleChangeReason_1000() {
            assertThat(violatedPaths(new SingleChangeRequestDTO(
                    DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType.EXPIRES_AT, "2030-01-01T00:00:00", "a".repeat(1001))))
                    .contains("reason");
        }
    }

    @Nested
    @DisplayName("마이그레이션")
    class Migrate {

        @Test
        @DisplayName("빈 노드 이름과 0~1 범위를 벗어난 개선 비율은 거절한다")
        void blankNodeAndRatioOutOfRange_areRejected() {
            MigratePodRequestDTO dto = new MigratePodRequestDTO(List.of("farm1", " "), 1.5, false);

            assertThat(violatedPaths(dto)).contains("nodes[1].<list element>", "minImprovementRatio");
        }

        @Test
        @DisplayName("비율을 생략하면 통과한다 (config-server 기본값 사용)")
        void ratioOmitted_isAccepted() {
            assertThat(validator.validate(new MigratePodRequestDTO(List.of("farm1"), null, null))).isEmpty();
        }
    }

    @Nested
    @DisplayName("사용자·그룹")
    class UsersAndGroups {

        @Test
        @DisplayName("유저네임 등록도 가입과 같은 계정 이름 규칙을 쓴다")
        void usernameRegister_usesSameRule() {
            assertThat(violatedPaths(new UbuntuUsernameRegisterRequestDTO("has_underscore"))).contains("ubuntuUsername");
            assertThat(violatedPaths(new UbuntuUsernameRegisterRequestDTO("hongildong"))).isEmpty();
        }

        @Test
        @DisplayName("새 비밀번호는 8~72자")
        void newPassword_bounds() {
            assertThat(violatedPaths(new PasswordUpdateRequestDTO("current", "1234567"))).contains("newPassword");
            assertThat(violatedPaths(new PasswordUpdateRequestDTO("current", "a".repeat(73)))).contains("newPassword");
            assertThat(violatedPaths(new PasswordUpdateRequestDTO("current", "12345678"))).isEmpty();
        }

        @Test
        @DisplayName("인증번호는 6자리 숫자")
        void emailCode_sixDigits() {
            assertThat(violatedPaths(new EmailVerifyRequestDTO("a@dgu.ac.kr", "12345"))).contains("code");
            assertThat(violatedPaths(new EmailVerifyRequestDTO("a@dgu.ac.kr", "12a456"))).contains("code");
            assertThat(violatedPaths(new EmailVerifyRequestDTO("a@dgu.ac.kr", "123456"))).isEmpty();
        }

        @Test
        @DisplayName("그룹명은 리눅스 그룹 규칙(32자, 밑줄 허용), 추가할 기존 계정 이름은 밑줄을 허용한다")
        void group_rules() {
            assertThat(violatedPaths(new CreateGroupRequestDTO("Developers", null))).contains("groupName");
            assertThat(violatedPaths(new CreateGroupRequestDTO("a".repeat(33), null))).contains("groupName");
            assertThat(violatedPaths(new CreateGroupRequestDTO("lab_members", "legacy_user"))).isEmpty();
            assertThat(violatedPaths(new CreateGroupRequestDTO("lab", "Bad User"))).contains("ubuntuUsername");
        }
    }
}
