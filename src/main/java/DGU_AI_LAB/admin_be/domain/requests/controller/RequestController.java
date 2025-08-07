package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.controller.docs.RequestApi;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.SaveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.RequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

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
    public ResponseEntity<RequestResponseDTO> getRequest(@PathVariable Long id) {
        return ResponseEntity.ok(requestService.getRequestById(id));
    }

    // 각 user의 사용 신청 목록 조회
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<RequestResponseDTO>> getRequestsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(requestService.getRequestsByUserId(userId));
    }
}