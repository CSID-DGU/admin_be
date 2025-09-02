package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.controller.docs.ConfigRequestApi;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.AcceptInfoResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.service.ConfigRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/requests/config")
public class ConfigRequestController implements ConfigRequestApi {

    private final ConfigRequestService configRequestService;

    @GetMapping("/check-username")
    public ResponseEntity<?> checkUbuntuUsername(@RequestParam String username) {
        boolean available = configRequestService.isUbuntuUsernameAvailable(username);
        return ResponseEntity.ok().body(
                java.util.Map.of(
                        "available", available
                )
        );
    }

    @GetMapping("/{username}")
    public ResponseEntity<AcceptInfoResponseDTO> getAcceptInfo(@PathVariable String username) {
        AcceptInfoResponseDTO response = configRequestService.getAcceptInfo(username);
        return ResponseEntity.ok(response);
    }

}
