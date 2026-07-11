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
                .ubuntuUsername("testuser")
                .ubuntuPassword("hashedPassword")
                .ubuntuPasswordBase64("base64Password")
                .volumeSizeGiB(50L)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .usagePurpose("딥러닝 연구")
                .formAnswers("{}")
                .user(user)
                .resourceGroup(rg)
                .containerImage(image)
                .build();
    }

    @Nested
    @DisplayName("approve")
    class Approve {

        @Test
        @DisplayName("승인하면 상태가 FULFILLED로 변경된다")
        void approve_changesStatusToFulfilled() {
            ContainerImage newImage = mock(ContainerImage.class);
            ResourceGroup newRg = mock(ResourceGroup.class);

            request.approve(newImage, newRg, 100L, "승인합니다");

            assertThat(request.getStatus()).isEqualTo(Status.FULFILLED);
            assertThat(request.getVolumeSizeGiB()).isEqualTo(100L);
            assertThat(request.getApprovedAt()).isNotNull();
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
            ContainerImage image = mock(ContainerImage.class);
            ResourceGroup rg = mock(ResourceGroup.class);
            request.approve(image, rg, 50L, null);

            assertThatThrownBy(request::delete)
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("deleteAfterCleanup")
    class DeleteAfterCleanup {

        @Test
        @DisplayName("FULFILLED 상태에서 인프라 정리 후 삭제하면 상태가 DELETED로 변경된다")
        void deleteAfterCleanup_changesStatusToDeleted_whenFulfilled() {
            ContainerImage image = mock(ContainerImage.class);
            ResourceGroup rg = mock(ResourceGroup.class);
            request.approve(image, rg, 50L, null);

            request.deleteAfterCleanup();

            assertThat(request.getStatus()).isEqualTo(Status.DELETED);
        }

        @Test
        @DisplayName("FULFILLED 이외의 상태에서 deleteAfterCleanup을 호출하면 BusinessException을 던진다")
        void deleteAfterCleanup_throwsException_whenNotFulfilled() {
            assertThatThrownBy(request::deleteAfterCleanup)
                    .isInstanceOf(BusinessException.class);
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
    @DisplayName("updateVolumeSize")
    class UpdateVolumeSize {

        @Test
        @DisplayName("null이 아닌 값으로 볼륨 크기를 업데이트한다")
        void updateVolumeSize_updatesWhenNotNull() {
            request.updateVolumeSize(200L);

            assertThat(request.getVolumeSizeGiB()).isEqualTo(200L);
        }

        @Test
        @DisplayName("null이면 볼륨 크기를 변경하지 않는다")
        void updateVolumeSize_doesNotUpdateWhenNull() {
            request.updateVolumeSize(null);

            assertThat(request.getVolumeSizeGiB()).isEqualTo(50L);
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
            assertThatThrownBy(() -> request.update(100L, null, "이유"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("FULFILLED 상태의 요청을 수정하면 성공한다")
        void update_success_whenFulfilled() {
            ContainerImage image = mock(ContainerImage.class);
            ResourceGroup rg = mock(ResourceGroup.class);
            request.approve(image, rg, 50L, null);

            LocalDateTime newDate = LocalDateTime.now().plusDays(90);
            request.update(200L, newDate, "용량 증가 필요");

            assertThat(request.getVolumeSizeGiB()).isEqualTo(200L);
            assertThat(request.getExpiresAt()).isEqualTo(newDate);
        }
    }

    @Nested
    @DisplayName("assignUbuntuIds")
    class AssignUbuntuIds {

        @Test
        @DisplayName("양수 UID/GID를 저장한다")
        void assignUbuntuIds_success() {
            request.assignUbuntuIds(2001L, 2001L);

            assertThat(request.getUbuntuUid()).isEqualTo(2001L);
            assertThat(request.getUbuntuGid()).isEqualTo(2001L);
        }

        @Test
        @DisplayName("UID/GID가 null 또는 양수가 아니면 BusinessException을 던진다")
        void assignUbuntuIds_throwsException_whenInvalid() {
            assertThatThrownBy(() -> request.assignUbuntuIds(null, 2001L))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> request.assignUbuntuIds(2001L, null))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> request.assignUbuntuIds(0L, 2001L))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> request.assignUbuntuIds(2001L, -1L))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
