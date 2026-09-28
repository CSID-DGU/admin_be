package DGU_AI_LAB.admin_be.global.auth;

import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailDomainPolicyTest {

    private final EmailDomainPolicy policy = new EmailDomainPolicy(List.of("dgu.ac.kr", " Dongguk.edu "));

    @Test
    @DisplayName("허용한 도메인만 대소문자 구분 없이 정확히 일치할 때 받는다")
    void allowsOnlyExactConfiguredDomains() {
        assertThat(policy.isAllowed("student@dgu.ac.kr")).isTrue();
        assertThat(policy.isAllowed("Prof@DONGGUK.EDU")).isTrue();

        assertThat(policy.isAllowed("a@gmail.com")).isFalse();
        assertThat(policy.isAllowed("a@mail.dgu.ac.kr")).isFalse();
        assertThat(policy.isAllowed("a@dgu.ac.kr.evil.com")).isFalse();
        assertThat(policy.isAllowed("a@evildgu.ac.kr")).isFalse();
        assertThat(policy.isAllowed("no-at-sign")).isFalse();
        assertThat(policy.isAllowed(null)).isFalse();
    }

    @Test
    @DisplayName("허용하지 않은 도메인은 BusinessException으로 거절한다")
    void requireAllowed_throwsForOtherDomains() {
        assertThatCode(() -> policy.requireAllowed("a@dgu.ac.kr")).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireAllowed("a@example.com")).isInstanceOf(BusinessException.class);
    }
}
