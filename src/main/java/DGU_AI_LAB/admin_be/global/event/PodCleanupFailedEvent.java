package DGU_AI_LAB.admin_be.global.event;

public record PodCleanupFailedEvent(
        String podName,
        String username
) {}
