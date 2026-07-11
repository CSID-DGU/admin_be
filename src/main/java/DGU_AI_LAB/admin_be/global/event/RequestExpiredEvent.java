package DGU_AI_LAB.admin_be.global.event;

public record RequestExpiredEvent(
        String userName,
        String userEmail,
        String ubuntuUsername,
        String serverName
) {}