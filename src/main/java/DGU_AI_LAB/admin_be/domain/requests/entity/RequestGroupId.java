package DGU_AI_LAB.admin_be.domain.requests.entity;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RequestGroupId implements Serializable {

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "ubuntu_gid")
    private Long ubuntuGid;
}
