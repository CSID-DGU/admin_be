package DGU_AI_LAB.admin_be.domain.requests.entity;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestGroupTest {

    @Test
    @DisplayName("builder: id(RequestGroupId)가 null이 아닌 빈 인스턴스로 초기화된다")
    void builder_initializesNonNullId() {
        // @MapsId가 flush 시 RequestGroupId 필드에 값을 채우려면
        // id 객체 자체가 null이 아니어야 한다 (null이면 NPE 발생)
        RequestGroup rg = RequestGroup.builder()
                .request(mock(Request.class))
                .group(mock(Group.class))
                .build();

        assertThat(rg.getId()).isNotNull();
    }

    @Test
    @DisplayName("builder: id의 requestId, ubuntuGid는 null이다 (@MapsId가 flush 시 채운다)")
    void builder_idFieldsAreNullBeforeFlush() {
        RequestGroup rg = RequestGroup.builder()
                .request(mock(Request.class))
                .group(mock(Group.class))
                .build();

        assertThat(rg.getId().getRequestId()).isNull();
        assertThat(rg.getId().getUbuntuGid()).isNull();
    }

    @Test
    @DisplayName("@PrePersist: createdAt이 null이면 현재 시각으로 설정된다")
    void prePersist_setsCreatedAtWhenNull() throws Exception {
        Request request = mock(Request.class);
        Group group = mock(Group.class);
        when(request.getRequestId()).thenReturn(1L);
        when(group.getUbuntuGid()).thenReturn(2000L);

        RequestGroup rg = RequestGroup.builder()
                .request(request)
                .group(group)
                .build();

        assertThat(rg.getCreatedAt()).isNull();

        invokeOnCreate(rg);

        assertThat(rg.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("@PrePersist: createdAt이 이미 있으면 덮어쓰지 않는다")
    void prePersist_doesNotOverrideExistingCreatedAt() throws Exception {
        Request request = mock(Request.class);
        Group group = mock(Group.class);
        when(request.getRequestId()).thenReturn(1L);
        when(group.getUbuntuGid()).thenReturn(2000L);

        RequestGroup rg = RequestGroup.builder()
                .request(request)
                .group(group)
                .build();

        invokeOnCreate(rg); // 첫 번째 호출 → createdAt 설정
        var firstCreatedAt = rg.getCreatedAt();

        invokeOnCreate(rg); // 두 번째 호출 → 덮어쓰지 않음

        assertThat(rg.getCreatedAt()).isEqualTo(firstCreatedAt);
    }

    @Test
    @DisplayName("@PrePersist: id가 builder에서 초기화됐으면 @PrePersist가 교체하지 않는다")
    void prePersist_doesNotOverrideExistingId() throws Exception {
        Request request = mock(Request.class);
        Group group = mock(Group.class);
        when(request.getRequestId()).thenReturn(1L);
        when(group.getUbuntuGid()).thenReturn(2000L);

        RequestGroup rg = RequestGroup.builder()
                .request(request)
                .group(group)
                .build();

        RequestGroupId idBeforePersist = rg.getId();

        invokeOnCreate(rg);

        // builder에서 이미 id가 설정됐으므로 @PrePersist는 새 인스턴스를 만들지 않는다
        assertThat(rg.getId()).isSameAs(idBeforePersist);
    }

    private void invokeOnCreate(RequestGroup rg) throws Exception {
        Method onCreate = RequestGroup.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        onCreate.invoke(rg);
    }
}
