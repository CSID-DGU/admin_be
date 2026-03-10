package DGU_AI_LAB.admin_be.domain.pod.repository;

import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PodExternalPortRepository extends JpaRepository<PodExternalPort, Long> {

    List<PodExternalPort> findByRequestRequestId(Long requestId);
}