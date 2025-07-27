package DGU_AI_LAB.admin_be.domain.pod.controller;

import io.fabric8.kubernetes.client.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/pods")
public class PodController {

    private final KubernetesClient client;

    @GetMapping
    public List<String> getPods() {
        return client.pods()
                .inNamespace("default")
                .list()
                .getItems()
                .stream()
                .map(pod -> pod.getMetadata().getName())
                .collect(Collectors.toList());
    }
}