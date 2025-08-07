package DGU_AI_LAB.admin_be.domain.users.repository;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {
    // TODO: username을 없애버린 것 같은데 그럼 이부분도 맞춰서 수정이 필요합니다! 어디서 쓰고있었는지??
    /* Optional<User> findByUsername(String username);
    Optional<User> findByUsernameAndIsActiveTrue(String username);*/
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailAndIsActiveTrue(String email); // 탈퇴 or 비활성 유저 차단 시 사용
}
