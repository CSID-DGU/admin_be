package DGU_AI_LAB.admin_be.domain.requests.entity;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "request_groups")
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class RequestGroup {

    @EmbeddedId
    private RequestGroupId id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY) @MapsId("requestId")
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @ManyToOne(fetch = FetchType.LAZY) @MapsId("ubuntuGid")
    @JoinColumn(name = "ubuntu_gid", nullable = false)
    private Group group;

    @Builder
    public RequestGroup(Request request, Group group) {
        this.request = request;
        this.group = group;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        // @MapsId가 id를 자동으로 채워주지만 방어적으로 ID도 보완
        if (id == null && request != null && group != null) {
            id = new RequestGroupId(request.getRequestId(), group.getUbuntuGid());
        }
    }
}
