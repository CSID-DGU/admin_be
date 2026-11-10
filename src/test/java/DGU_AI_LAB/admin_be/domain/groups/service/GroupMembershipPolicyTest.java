package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GroupMembershipPolicy")
class GroupMembershipPolicyTest {

    private final GroupMembershipPolicy policy = new GroupMembershipPolicy();

    private static Group group(long id) {
        Group group = Group.builder().groupName("g" + id).build();
        ReflectionTestUtils.setField(group, "groupId", id);
        return group;
    }

    private static User userInGroups(int count) {
        User user = User.builder().email("a@dgu.ac.kr").password("pw").name("홍").studentId("1")
                .phone("010").department("과").build();
        IntStream.rangeClosed(1, count).forEach(i -> user.addGroupIfAbsent(group(i)));
        return user;
    }

    @Test
    @DisplayName("지금 속한 그룹과 새로 넣을 그룹을 합쳐 5개면 허용한다")
    void allowsUpToFive() {
        assertThatCode(() -> policy.requireRoomFor(userInGroups(3), List.of(group(10), group(11))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("합쳐서 5개를 넘으면 GROUP_LIMIT_EXCEEDED")
    void rejectsOverFive() {
        assertThatThrownBy(() -> policy.requireRoomFor(userInGroups(4), List.of(group(10), group(11))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("이미 속한 그룹은 새로 세지 않는다")
    void doesNotCountGroupsAlreadyJoined() {
        assertThatCode(() -> policy.requireRoomFor(userInGroups(5), List.of(group(1), group(2))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("넣을 그룹이 없으면 이미 5개여도 통과한다")
    void emptyJoinIsAllowed() {
        assertThatCode(() -> policy.requireRoomFor(userInGroups(5), List.of())).doesNotThrowAnyException();
    }
}
