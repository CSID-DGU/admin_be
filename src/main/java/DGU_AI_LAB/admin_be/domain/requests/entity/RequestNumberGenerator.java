package DGU_AI_LAB.admin_be.domain.requests.entity;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.EventTypeSets;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;

/**
 * 신청 번호를 대장(request_numbers)에서 발급한다. DB 자동 증가를 쓰지 않는 것은 번호가 빠짐없이 이어져야 하기
 * 때문이다 — 자동 증가는 저장이 되돌려져도 올라간 값을 돌려놓지 않는다. 여기서는 신청을 저장하는 그 연결에서
 * 대장 행을 잠그고 올리므로, 저장이 되돌려지면 번호도 함께 돌아오고 동시에 들어온 신청은 앞 신청이 끝날 때까지
 * 기다린다. 대장은 줄어들지 않으므로 신청 행을 지워도 같은 번호가 다시 나오지 않는다.
 */
public class RequestNumberGenerator implements BeforeExecutionGenerator {

    private static final String LOCK_NEXT = "SELECT next_number FROM request_numbers WHERE id = 1 FOR UPDATE";
    private static final String ADVANCE = "UPDATE request_numbers SET next_number = ? WHERE id = 1";

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue,
                           EventType eventType) {
        Connection connection = session.getJdbcCoordinator().getLogicalConnection().getPhysicalConnection();
        try {
            long number = lockNext(connection);
            advance(connection, number + 1);
            return number;
        } catch (SQLException e) {
            throw new HibernateException("신청 번호를 발급하지 못했습니다.", e);
        }
    }

    private long lockNext(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_NEXT);
             ResultSet row = statement.executeQuery()) {
            if (!row.next()) {
                throw new HibernateException("신청 번호 대장(request_numbers)에 행이 없습니다.");
            }
            return row.getLong(1);
        }
    }

    private void advance(Connection connection, long next) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ADVANCE)) {
            statement.setLong(1, next);
            statement.executeUpdate();
        }
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EventTypeSets.INSERT_ONLY;
    }
}
