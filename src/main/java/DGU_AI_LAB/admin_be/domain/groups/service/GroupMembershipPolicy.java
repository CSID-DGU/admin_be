package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** 한 사람이 속할 수 있는 공용 그룹 수의 한도. 새로 소속이 늘어나는 경로(신청서·그룹 추가 변경 요청)가 같은 규칙을 쓴다. */
@Component
public class GroupMembershipPolicy {

    static final int MAX_GROUPS_PER_USER = 5;

    /**
     * 지금 속한 그룹에 {@code groupsToJoin}을 더해도 한도 안인지 확인한다. 이미 속한 그룹은 새로 세지 않는다.
     *
     * @throws BusinessException 한도를 넘으면 GROUP_LIMIT_EXCEEDED
     */
    public void requireRoomFor(User user, Collection<Group> groupsToJoin) {
        Set<Long> current = user.getUserGroups().stream()
                .map(ug -> ug.getGroup().getGroupId()).collect(Collectors.toSet());
        long added = groupsToJoin.stream().map(Group::getGroupId).filter(id -> !current.contains(id)).distinct().count();
        if (added > 0 && current.size() + added > MAX_GROUPS_PER_USER) {
            throw new BusinessException(ErrorCode.GROUP_LIMIT_EXCEEDED);
        }
    }
}
