package DGU_AI_LAB.admin_be.domain.scheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PodCleanupTask implements Serializable {

    private String podName;
    private String username;
    private int retryCount;

    public static final int MAX_RETRIES = 5;

    public boolean isRetryExhausted() {
        return retryCount >= MAX_RETRIES;
    }

    public PodCleanupTask incrementRetry() {
        this.retryCount++;
        return this;
    }
}
