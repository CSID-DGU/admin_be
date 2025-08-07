package DGU_AI_LAB.admin_be.domain.image.repository;

import DGU_AI_LAB.admin_be.domain.image.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {
}