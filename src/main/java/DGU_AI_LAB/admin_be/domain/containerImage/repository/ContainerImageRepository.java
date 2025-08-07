package DGU_AI_LAB.admin_be.domain.containerImage.repository;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImageId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContainerImageRepository extends JpaRepository<ContainerImage, ContainerImageId> {
    Optional<ContainerImage> findByImageNameAndImageVersion(String imageName, String imageVersion);
}
