package DGU_AI_LAB.admin_be.domain.users.entity;

import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UbuntuAccountTest {

    private UbuntuAccount account;

    @BeforeEach
    void setUp() {
        account = new UbuntuAccount(null, "LAB", "hong");
    }

    @Test
    void startsWithoutAccount() {
        assertThat(account.getStatus()).isEqualTo(UbuntuAccountStatus.NONE);
        assertThat(account.isActive()).isFalse();
        assertThat(account.getUid()).isNull();
    }

    @Test
    void assign_isIdempotentAndRejectsConflictingIds() {
        account.assign(10002L, 10002L);
        account.assign(10002L, 10002L);

        assertThat(account.isActive()).isTrue();
        assertThatThrownBy(() -> account.assign(10003L, 10003L)).isInstanceOf(BusinessException.class);
        assertThat(account.getUid()).isEqualTo(10002L);
    }

    @Test
    void assign_rejectsInvalidIds() {
        assertThatThrownBy(() -> account.assign(null, 1L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> account.assign(1L, 0L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void assign_rejectedWhileReleasing() {
        account.assign(10002L, 10002L);
        account.beginRelease();

        assertThatThrownBy(() -> account.assign(10002L, 10002L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void releaseLifecycle_keepsIdentity() {
        assertThat(account.beginRelease()).isFalse();
        assertThatThrownBy(account::release).isInstanceOf(BusinessException.class);

        account.assign(10002L, 10002L);
        assertThatThrownBy(account::release).isInstanceOf(BusinessException.class);
        assertThat(account.beginRelease()).isTrue();
        assertThat(account.beginRelease()).isTrue();
        assertThat(account.isReleasing()).isTrue();

        account.release();
        assertThat(account.getStatus()).isEqualTo(UbuntuAccountStatus.NONE);
        assertThat(account.getUid()).isEqualTo(10002L);
        assertThat(account.getUsername()).isEqualTo("hong");

        account.assign(10002L, 10002L);
        assertThat(account.isActive()).isTrue();
    }

    @Test
    void abortRelease_restoresActive() {
        account.assign(10002L, 10002L);
        account.beginRelease();

        account.abortRelease();

        assertThat(account.isActive()).isTrue();
        assertThatThrownBy(account::abortRelease).isInstanceOf(BusinessException.class);
    }

    @Test
    void sameServer_matchesIgnoringCase() {
        assertThat(account.isServer("lab")).isTrue();
        assertThat(account.isServer("FARM")).isFalse();
    }
}
