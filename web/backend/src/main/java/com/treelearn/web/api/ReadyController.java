package com.treelearn.web.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Readiness contract for the local service (task T-2026-10-10-003).
 *
 * <p>The response shape is provisional: the single authoritative API/SSE
 * contract is created under web/contracts by the contract task and may extend
 * this payload. Field values are kept stable until then so the start/stop
 * scripts and the verifier can rely on them.
 */
@RestController
@RequestMapping("/api/health")
public class ReadyController {

    @GetMapping("/ready")
    public Map<String, String> ready() {
        return Map.of(
                "service", "treelearn-web-backend",
                "status", "ready");
    }
}
