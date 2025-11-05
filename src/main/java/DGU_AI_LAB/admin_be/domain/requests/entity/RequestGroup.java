package DGU_AI_LAB.admin_be.domain.requests.entity;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "request_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = {"request", "group"})
public class RequestGroup {

    @EmbeddedId
    private RequestGroupId id;

    @ManyToOne(fetch = FetchType.LAZY) @MapsId("requestId")
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @ManyToOne(fetch = FetchType.LAZY) @MapsId("ubuntuGid")
    @JoinColumn(name = "ubuntu_gid", nullable = false)
    private Group group;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public RequestGroup(Request request, Group group) {
        this.request = request;
        this.group = group;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }

        if (this.id == null) {
            this.id = new RequestGroupId(this.request.getRequestId(), this.group.getUbuntuGid());
        }
    }
}