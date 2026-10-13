package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DmRecipientEmails")
class DmRecipientEmailsTest {

    @InjectMocks
    private DmRecipientEmails dmRecipientEmails;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("회원 정보에 있는 이메일(학교 이메일 + 자주 사용하는 이메일)을 모두 돌려준다")
    void returnsEveryKnownEmail() {
        User user = mock(User.class);
        when(user.knownEmails()).thenReturn(List.of("hong@dgu.ac.kr", "hong@gmail.com"));
        when(userRepository.findByEmail("hong@dgu.ac.kr")).thenReturn(Optional.of(user));

        assertThat(dmRecipientEmails.of("hong@dgu.ac.kr")).containsExactly("hong@dgu.ac.kr", "hong@gmail.com");
    }

    @Test
    @DisplayName("회원 정보가 없으면 받은 이메일 하나만 쓰고, 이메일이 없으면 빈 목록이다")
    void fallsBackWhenUserUnknown() {
        when(userRepository.findByEmail("gone@dgu.ac.kr")).thenReturn(Optional.empty());

        assertThat(dmRecipientEmails.of("gone@dgu.ac.kr")).containsExactly("gone@dgu.ac.kr");
        assertThat(dmRecipientEmails.of(null)).isEmpty();
    }
}
