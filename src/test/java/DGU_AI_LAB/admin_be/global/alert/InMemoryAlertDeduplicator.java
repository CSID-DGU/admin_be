package DGU_AI_LAB.admin_be.global.alert;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 시험용 대역. Redis 없이 "같은 사건은 한 번만" 동작을 그대로 흉내낸다. */
public class InMemoryAlertDeduplicator implements AlertDeduplicator {

    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    @Override
    public boolean firstOccurrence(String eventKey) {
        return seen.add(eventKey);
    }
}
