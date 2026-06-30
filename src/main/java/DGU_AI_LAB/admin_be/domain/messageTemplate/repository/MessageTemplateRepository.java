package DGU_AI_LAB.admin_be.domain.messageTemplate.repository;

import DGU_AI_LAB.admin_be.domain.messageTemplate.entity.MessageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, String> {
}
