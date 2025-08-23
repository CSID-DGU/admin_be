package DGU_AI_LAB.admin_be.domain.resourceGroupImages.repository;

import DGU_AI_LAB.admin_be.domain.resourceGroupImages.entity.ResourceGroupImage;
import DGU_AI_LAB.admin_be.domain.resourceGroupImages.entity.ResourceGroupImageId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceGroupImageRepository extends JpaRepository<ResourceGroupImage, ResourceGroupImageId> {
    
    @Query("SELECT rgi FROM ResourceGroupImage rgi WHERE rgi.resourceGroup.rsgroupId = :rsgroupId")
    List<ResourceGroupImage> findByResourceGroupId(@Param("rsgroupId") Integer rsgroupId);
    
    @Query("SELECT rgi FROM ResourceGroupImage rgi WHERE rgi.containerImage.imageId = :imageId")
    List<ResourceGroupImage> findByContainerImageId(@Param("imageId") Long imageId);
}