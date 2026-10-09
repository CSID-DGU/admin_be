package DGU_AI_LAB.admin_be.global.event;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 신청·포트 엔티티가 DB에 반영될 때 {@link RequestRowChangedEvent}를 낸다. 상태를 바꾸는 서비스마다 따로 알리지 않고
 * 저장 지점 한 곳에서 내므로, 전이가 늘어도 빠뜨리지 않는다. 엔티티를 거치지 않는 일괄 수정 쿼리는 잡지 못한다.
 *
 * <p>아직 커밋 전이다. 받는 쪽은 {@code @TransactionalEventListener(AFTER_COMMIT)}으로 받아야 한다.
 */
public class RequestRowChangeListener {

    private final ApplicationEventPublisher eventPublisher;

    public RequestRowChangeListener(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    public void changed(Object entity) {
        eventPublisher.publishEvent(new RequestRowChangedEvent());
    }
}
