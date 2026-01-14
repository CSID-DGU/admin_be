package DGU_AI_LAB.admin_be.global.event;

import DGU_AI_LAB.admin_be.domain.users.entity.User;

public record RequestExpiredEvent(
        User user,
        String ubuntuUsername,
        String serverName // Lab/Farm 구분용
) {}