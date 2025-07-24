package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.controller.docs.RequestApi;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.SaveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController implements RequestApi {

    private final RequestService requestService;

    @PostMapping
    public ResponseEntity<?> saveRequest(@Valid @RequestBody SaveRequestDTO request) {
        return ResponseEntity.ok(requestService.saveRequest(request));
    }

    // 전체 사용 신청 목록 조회
    @GetMapping
    public ResponseEntity<?> getAllRequests() {
        return ResponseEntity.ok(requestService.getAllRequests());
    }

    // 개별 사용 신청 목록 조회
    @GetMapping("/{id}")
    public ResponseEntity<SaveRequestDTO> getRequest(@PathVariable Long id) {
        return ResponseEntity.ok(requestService.getRequestById(id));
    }
}