package DGU_AI_LAB.admin_be.domain.requests.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 다음에 발급할 신청 번호를 적어 둔 한 행짜리 대장. 읽고 올리는 일은 {@link RequestNumberGenerator}가 한다.
 */
@Entity
@Table(name = "request_numbers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestNumber {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "next_number", nullable = false)
    private Long nextNumber;
}
