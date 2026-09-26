package DGU_AI_LAB.admin_be.domain.requests.entity;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RequestTest {

    private Request request;

    @BeforeEach
    void setUp() {
        User user = mock(User.class);
        ResourceGroup rg = mock(ResourceGroup.class);
        ContainerImage image = mock(ContainerImage.class);

        request = Request.builder()
                .expiresAt(LocalDateTime.now().plusDays(30))
                .usagePurpose("딥러닝 연구")
                .formAnswers("{}")
                .user(user)
                .resourceGroup(rg)
                .containerImage(image)
                .build();
    }

    /** 승인 경로(PENDING → PROCESSING → FULFILLED)를 그대로 밟아 FULFILLED로 만든다. */
    private void fulfill() {
        request.markAsProcessing();
        request.prepareAsyncApproval(mock(ContainerImage.class), mock(ResourceGroup.class), null);
        request.completeApproval();
    }

    @Nested
    @DisplayName("completeApproval")
    class CompleteApproval {

        @Test
        @DisplayName("처리 중인 신청의 생성 작업이 성공하면 FULFILLED가 되고 승인 시각이 남는다")
        void completeApproval_changesStatusToFulfilled() {
            fulfill();

            assertThat(request.getStatus()).isEqualTo(Status.FULFILLED);
            assertThat(request.getApprovedAt()).isNotNull();
        }

        @Test
        @DisplayName("PROCESSING을 거치지 않은 신청은 승인을 확정할 수 없다")
        void completeApproval_throwsException_whenPending() {
            assertThatThrownBy(request::completeApproval)
                    .isInstanceOf(BusinessException.class);
            assertThat(request.getStatus()).isEqualTo(Status.PENDING);
        }
    }

    @Nested
    @DisplayName("revertToPending")
    class RevertToPending {

        @Test
        @DisplayName("PENDING으로 되돌리면 이미 지운 자원을 가리키는 podName/nodeName과 작업 번호를 지운다")
        void revertToPending_clearsPodInfoAndJob() {
            request.markAsProcessing();
            request.recordJob(7L);
            request.assignPodInfo("ailab-testuser-abcd1234", "farm1");

            request.revertToPending();

            assertThat(request.getStatus()).isEqualTo(Status.PENDING);
            assertThat(request.getPodName()).isNull();
            assertThat(request.getNodeName()).isNull();
            assertThat(request.getJobId()).isNull();
        }

        @Test
        @DisplayName("처리 중이 아닌 신청은 되돌릴 수 없다 — FULFILLED를 PENDING으로 돌리면 컨테이너가 두 번 만들어진다")
        void revertToPending_throwsException_whenFulfilled() {
            fulfill();

            assertThatThrownBy(request::revertToPending)
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("reject")
    class Reject {

        @Test
        @DisplayName("거절하면 상태가 DENIED로 변경된다")
        void reject_changesStatusToDenied() {
            request.reject("리소스 부족");

            assertThat(request.getStatus()).isEqualTo(Status.DENIED);
            assertThat(request.getAdminComment()).isEqualTo("리소스 부족");
        }

        @Test
        @DisplayName("컨테이너가 떠 있는 FULFILLED 신청은 거절할 수 없다 — 거절로는 자원이 회수되지 않는다")
        void reject_throwsException_whenFulfilled() {
            fulfill();

            assertThatThrownBy(() -> request.reject("승인 취소"))
                    .isInstanceOf(BusinessException.class);
            assertThat(request.getStatus()).isEqualTo(Status.FULFILLED);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("PENDING 상태에서 삭제하면 상태가 DELETED로 변경된다")
        void delete_changesStatusToDeleted_whenPending() {
            request.delete();

            assertThat(request.getStatus()).isEqualTo(Status.DELETED);
        }

        @Test
        @DisplayName("DENIED 상태에서 삭제하면 상태가 DELETED로 변경된다")
        void delete_changesStatusToDeleted_whenDenied() {
            request.reject("리소스 부족");

            request.delete();

            assertThat(request.getStatus()).isEqualTo(Status.DELETED);
        }

        @Test
        @DisplayName("이미 삭제된 Request를 다시 삭제하면 BusinessException을 던진다")
        void delete_throwsException_whenAlreadyDeleted() {
            request.delete();

            assertThatThrownBy(request::delete)
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("FULFILLED 상태의 Request를 delete()로 삭제하면 BusinessException을 던진다")
        void delete_throwsException_whenFulfilled() {
            fulfill();

            assertThatThrownBy(request::delete)
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("MIGRATING 상태의 Request를 delete()로 삭제하면 BusinessException을 던진다")
        void delete_throwsException_whenMigrating() {
            fulfill();
            request.beginMigration();

            assertThatThrownBy(request::delete)
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("PROCESSING 상태의 Request를 delete()로 삭제하면 BusinessException을 던진다")
        void delete_throwsException_whenProcessing() {
            // 관리자가 승인 처리 중(AD 계정/Pod 생성이 백그라운드에서 진행 중)인 요청을
            // 사용자가 취소하면, 처리 완료 시점에 DB에 추적되지 않는 고아 계정/Pod가
            // 생길 수 있다. delete()는 이 상태도 반드시 막아야 한다.
            request.markAsProcessing();

            assertThatThrownBy(request::delete)
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("deleteAfterCleanup")
    class DeleteAfterCleanup {

        @Test
        @DisplayName("EXPIRING 상태에서 인프라 정리 후 삭제하면 상태가 DELETED로 변경된다")
        void deleteAfterCleanup_changesStatusToDeleted_whenExpiring() {
            fulfill();
            request.beginExpiry();

            request.deleteAfterCleanup();

            assertThat(request.getStatus()).isEqualTo(Status.DELETED);
        }

        @Test
        @DisplayName("EXPIRING 이외의 상태에서 deleteAfterCleanup을 호출하면 BusinessException을 던진다")
        void deleteAfterCleanup_throwsException_whenNotExpiring() {
            assertThatThrownBy(request::deleteAfterCleanup)
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("정리를 선점(beginExpiry)하지 않은 FULFILLED 상태에서는 deleteAfterCleanup이 거부된다")
        void deleteAfterCleanup_throwsException_whenFulfilledWithoutClaim() {
            fulfill();

            assertThatThrownBy(request::deleteAfterCleanup)
                    .isInstanceOf(BusinessException.class);
            assertThat(request.getStatus()).isEqualTo(Status.FULFILLED);
        }

        @Test
        @DisplayName("인프라 정리 후 삭제해도 podName/nodeName은 이력 조회용으로 남는다")
        void deleteAfterCleanup_keepsPodInfo() {
            fulfill();
            request.assignPodInfo("ailab-testuser-abcd1234", "farm1");
            request.beginExpiry();

            request.deleteAfterCleanup();

            assertThat(request.getPodName()).isEqualTo("ailab-testuser-abcd1234");
            assertThat(request.getNodeName()).isEqualTo("farm1");
        }

        @Test
        @DisplayName("DENIED 상태에서 deleteAfterCleanup을 호출하면 BusinessException을 던진다")
        void deleteAfterCleanup_throwsException_whenDenied() {
            request.reject("리소스 부족");

            assertThatThrownBy(request::deleteAfterCleanup)
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("updateExpiresAt")
    class UpdateExpiresAt {

        @Test
        @DisplayName("null이 아닌 값으로 만료일을 업데이트한다")
        void updateExpiresAt_updatesWhenNotNull() {
            LocalDateTime newDate = LocalDateTime.now().plusDays(60);
            request.updateExpiresAt(newDate);

            assertThat(request.getExpiresAt()).isEqualTo(newDate);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("FULFILLED 상태가 아닌 요청을 수정하면 BusinessException을 던진다")
        void update_throwsException_whenNotFulfilled() {
            assertThatThrownBy(() -> request.update(null, "이유"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("FULFILLED 상태의 요청을 수정하면 성공한다")
        void update_success_whenFulfilled() {
            fulfill();

            LocalDateTime newDate = LocalDateTime.now().plusDays(90);
            request.update(newDate, "용량 증가 필요");

            assertThat(request.getExpiresAt()).isEqualTo(newDate);
        }
    }

    @Nested
    @DisplayName("작업 번호(jobId)")
    class JobId {

        @Test
        @DisplayName("회수 시작은 이전 작업 번호를 비운다 — 폴러가 생성 작업의 결과를 회수 결과로 착각하지 않게")
        void beginExpiryClearsJob() {
            fulfill();
            request.recordJob(10L);

            request.beginExpiry();

            assertThat(request.getJobId()).isNull();
        }

        @Test
        @DisplayName("DELETED로 넘어가면 비운다 — DELETED 신청의 작업 번호는 계정 회수 작업 번호 전용이다")
        void deletedClearsJob() {
            fulfill();
            request.beginExpiry();
            request.recordJob(20L);

            request.deleteAfterCleanup();

            assertThat(request.getJobId()).isNull();
        }

        @Test
        @DisplayName("취소(delete)도 비운다")
        void cancelClearsJob() {
            request.recordJob(30L);

            request.delete();

            assertThat(request.getJobId()).isNull();
        }

        @Test
        @DisplayName("계정 회수 작업 번호는 DELETED 신청에서만 비울 수 있다")
        void forgetAccountRevokeJobOnlyWhenDeleted() {
            fulfill();
            request.recordJob(40L);
            assertThatThrownBy(request::forgetAccountRevokeJob).isInstanceOf(BusinessException.class);

            request.beginExpiry();
            request.deleteAfterCleanup();
            request.recordJob(50L);
            request.forgetAccountRevokeJob();

            assertThat(request.getJobId()).isNull();
        }
    }

    @Nested
    @DisplayName("beginExpiry / endExpiry")
    class Expiry {

        @Test
        @DisplayName("FULFILLED 요청의 정리를 시작하면 EXPIRING으로 전환된다")
        void beginExpiry_transitionsFulfilledToExpiring() {
            fulfill();

            request.beginExpiry();

            assertThat(request.getStatus()).isEqualTo(Status.EXPIRING);
        }

        @Test
        @DisplayName("이미 EXPIRING인 요청의 정리를 다시 시작하면 BusinessException을 던진다 (중복 정리 차단)")
        void beginExpiry_throwsException_whenAlreadyExpiring() {
            fulfill();
            request.beginExpiry();

            assertThatThrownBy(request::beginExpiry)
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("PROCESSING 상태(승인 처리 중)인 요청은 정리를 시작할 수 없다")
        void beginExpiry_throwsException_whenProcessing() {
            request.markAsProcessing();

            assertThatThrownBy(request::beginExpiry)
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("MIGRATING 상태인 요청은 정리를 시작할 수 없다")
        void beginExpiry_throwsException_whenMigrating() {
            fulfill();
            request.beginMigration();

            assertThatThrownBy(request::beginExpiry)
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("정리에 실패해 되돌리면 FULFILLED로 돌아가 다음 만료 스케줄에서 재시도된다")
        void endExpiry_revertsToFulfilled() {
            fulfill();
            request.beginExpiry();

            request.endExpiry();

            assertThat(request.getStatus()).isEqualTo(Status.FULFILLED);
        }

        @Test
        @DisplayName("EXPIRING이 아닌 요청을 되돌리려 하면 BusinessException을 던진다")
        void endExpiry_throwsException_whenNotExpiring() {
            fulfill();

            assertThatThrownBy(request::endExpiry)
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("EXPIRING 중인 요청은 사용자가 취소(delete)할 수 없다 — 인프라 정리 중이라 고아 리소스가 생긴다")
        void delete_throwsException_whenExpiring() {
            fulfill();
            request.beginExpiry();

            assertThatThrownBy(request::delete)
                    .isInstanceOf(BusinessException.class);
            assertThat(request.getStatus()).isEqualTo(Status.EXPIRING);
        }
    }
}
