package DGU_AI_LAB.admin_be.domain.portRequests.entity;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "port_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortRequests extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "port_request_id")
    private Long portRequestId;

    @Column(name = "internal_port", nullable = false)
    private Integer internalPort;

    @Column(name = "usage_purpose", nullable = false, length = 1000)
    private String usagePurpose;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rsgroup_id", nullable = false)
    private ResourceGroup resourceGroup;

    @Builder
    public PortRequests(Integer internalPort, String usagePurpose, Request request, ResourceGroup resourceGroup) {
        this.internalPort = internalPort;
        this.usagePurpose = usagePurpose;
        this.request = request;
        this.resourceGroup = resourceGroup;
    }

    public void activate() {
        this.isActive = true;
    }
}