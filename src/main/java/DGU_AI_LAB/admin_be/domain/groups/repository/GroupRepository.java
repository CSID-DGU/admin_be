package DGU_AI_LAB.admin_be.domain.groups.repository;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {
    List<Group> findAllByUbuntuGidIn(Set<Long> ubuntuGids);
    boolean existsByUbuntuGid(Long ubuntuGid);
    Optional<Group> findByUbuntuGid(Long ubuntuGid);
}