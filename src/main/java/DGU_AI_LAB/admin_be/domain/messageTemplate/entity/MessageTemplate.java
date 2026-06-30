package DGU_AI_LAB.admin_be.domain.messageTemplate.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageTemplate {

    @Id
    @Column(name = "template_key", length = 100)
    private String key;

    @Column(name = "template_value", nullable = false, columnDefinition = "TEXT")
    private String value;

    private LocalDateTime updatedAt;

    public MessageTemplate(String key, String value) {
        this.key = key;
        this.value = value;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateValue(String value) {
        this.value = value;
        this.updatedAt = LocalDateTime.now();
    }
}
