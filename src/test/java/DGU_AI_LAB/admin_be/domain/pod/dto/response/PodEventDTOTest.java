package DGU_AI_LAB.admin_be.domain.pod.dto.response;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PodEventDTO.fromEntity")
class PodEventDTOTest {

    @Test
    @DisplayName("lastTimestamp가 있으면 그대로 사용한다")
    void usesLastTimestamp_whenPresent() {
        Event event = new EventBuilder()
                .withType("Warning")
                .withReason("FailedScheduling")
                .withMessage("0/5 nodes are available")
                .withCount(2)
                .withFirstTimestamp("2026-09-01T09:00:00Z")
                .withLastTimestamp("2026-09-01T09:05:00Z")
                .build();

        PodEventDTO result = PodEventDTO.fromEntity(event);

        assertThat(result.type()).isEqualTo("Warning");
        assertThat(result.reason()).isEqualTo("FailedScheduling");
        assertThat(result.message()).isEqualTo("0/5 nodes are available");
        assertThat(result.count()).isEqualTo(2);
        assertThat(result.lastTimestamp()).isEqualTo("2026-09-01T09:05:00Z");
    }

    @Test
    @DisplayName("lastTimestamp가 없으면 firstTimestamp로 대체한다")
    void fallsBackToFirstTimestamp_whenLastTimestampMissing() {
        Event event = new EventBuilder()
                .withType("Normal")
                .withReason("Scheduled")
                .withFirstTimestamp("2026-09-01T09:00:00Z")
                .build();

        PodEventDTO result = PodEventDTO.fromEntity(event);

        assertThat(result.lastTimestamp()).isEqualTo("2026-09-01T09:00:00Z");
    }
}
