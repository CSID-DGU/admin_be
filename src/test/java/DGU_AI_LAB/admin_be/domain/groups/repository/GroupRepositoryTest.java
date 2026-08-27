package DGU_AI_LAB.admin_be.domain.groups.repository;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class GroupRepositoryTest {

    @Autowired
    private GroupRepository groupRepository;

    @Test
    @DisplayName("같은 그룹명을 두 번 저장하면 unique 제약에 걸린다")
    void groupName_hasUniqueConstraint() {
        groupRepository.saveAndFlush(Group.builder().groupName("developers").ubuntuGid(2000L).build());

        Group duplicate = Group.builder().groupName("developers").ubuntuGid(2001L).build();

        assertThatThrownBy(() -> groupRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("그룹명이 다르면 정상 저장된다")
    void groupName_allowsDistinctNames() {
        groupRepository.saveAndFlush(Group.builder().groupName("developers").ubuntuGid(2000L).build());
        groupRepository.saveAndFlush(Group.builder().groupName("researchers").ubuntuGid(2001L).build());

        assertThat(groupRepository.findAll()).hasSize(2);
    }
}
