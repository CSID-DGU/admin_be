package DGU_AI_LAB.admin_be.domain.nodes.controller;

import DGU_AI_LAB.admin_be.domain.nodes.controller.docs.AdminNodeApi;
import DGU_AI_LAB.admin_be.domain.nodes.service.NodeQueryService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/nodes")
public class AdminNodeController implements AdminNodeApi {

    private final NodeQueryService nodeQueryService;

    @GetMapping
    public ResponseEntity<SuccessResponse<?>> getAllNodes() {
        return SuccessResponse.ok(nodeQueryService.getAllNodes());
    }
}
