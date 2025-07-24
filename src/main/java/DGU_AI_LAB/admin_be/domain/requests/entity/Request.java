package DGU_AI_LAB.admin_be.domain.requests.entity;

import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Entity
@Table(name = "requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Request extends BaseTimeEntity  {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requestId;

    @Column(name = "serverName", nullable = false)
    private String serverName; // LAB or FARM

    @Builder.Default
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Answer> answers = new ArrayList<>();
}