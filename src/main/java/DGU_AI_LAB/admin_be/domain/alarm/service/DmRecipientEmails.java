package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Slack DM을 받을 회원을 찾을 때 쓸 이메일. 알림은 학교 이메일만 들고 오지만, 그 사람이 자주 사용하는 이메일로
 * Slack에 가입했을 수 있으므로 회원 정보에 있는 이메일을 모두 돌려준다. 보내는 시점에 조회한다 — 큐에 들어간 뒤
 * 바뀐 이메일도 반영되고, 큐 항목의 형식을 바꾸지 않아도 된다.
 */
@Component
@RequiredArgsConstructor
public class DmRecipientEmails {

    private final UserRepository userRepository;

    /** 회원 정보가 없으면(탈퇴 등) 받은 이메일 하나만 쓴다. */
    public List<String> of(String email) {
        if (email == null) {
            return List.of();
        }
        return userRepository.findByEmail(email).map(User::knownEmails).orElseGet(() -> List.of(email));
    }
}
