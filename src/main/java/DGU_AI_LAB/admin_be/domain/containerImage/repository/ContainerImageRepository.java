package DGU_AI_LAB.admin_be.domain.containerImage.repository;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContainerImageRepository extends JpaRepository<ContainerImage, Long> {
    boolean existsByImageNameAndImageVersion(String imageName, String imageVersion);
}
