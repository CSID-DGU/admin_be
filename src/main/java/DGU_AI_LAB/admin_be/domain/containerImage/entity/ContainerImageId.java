package DGU_AI_LAB.admin_be.domain.containerImage.entity;

import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContainerImageId implements Serializable {
    private String imageName;
    private String imageVersion;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContainerImageId that)) return false;
        return Objects.equals(imageName, that.imageName) &&
                Objects.equals(imageVersion, that.imageVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(imageName, imageVersion);
    }
}
