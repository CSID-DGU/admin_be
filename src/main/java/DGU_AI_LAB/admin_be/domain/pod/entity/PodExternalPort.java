package DGU_AI_LAB.admin_be.domain.pod.entity;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pod_external_ports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PodExternalPort extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pod_external_port_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @Column(name = "internal_port", nullable = false)
    @Min(1)
    @Max(65535)
    private Integer internalPort;

    @Column(name = "external_port", nullable = false)
    @Min(1)
    @Max(65535)
    private Integer externalPort;

    @Column(name = "usage_purpose", nullable = false, length = 1000)
    private String usagePurpose;

    @Builder
    public PodExternalPort(Request request, Integer internalPort, Integer externalPort, String usagePurpose) {
        this.request = request;
        this.internalPort = internalPort;
        this.externalPort = externalPort;
        this.usagePurpose = usagePurpose;
    }
}