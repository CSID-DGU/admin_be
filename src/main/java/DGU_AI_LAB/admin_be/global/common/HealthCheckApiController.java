package DGU_AI_LAB.admin_be.global.common;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RequiredArgsConstructor
@RestController
public class HealthCheckApiController {

    // 서버 상태 확인 API
    @RequestMapping("/")
    public String healthCheck() {
        return "OK";
    }
}
