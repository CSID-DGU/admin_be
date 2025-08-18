package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.controller.docs.AcceptInfoApi;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.AcceptInfoResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/acceptinfo")
public class AcceptInfoController implements AcceptInfoApi {
    private final RequestService requestService;

    @GetMapping("/{username}")
    public ResponseEntity<AcceptInfoResponseDTO> getAcceptInfo(@PathVariable String username) {
        AcceptInfoResponseDTO response = requestService.getAcceptInfo(username);
        return ResponseEntity.ok(response);
    }
}