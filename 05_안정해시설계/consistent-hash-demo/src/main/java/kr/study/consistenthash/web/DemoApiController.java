package kr.study.consistenthash.web;

import kr.study.consistenthash.domain.ConsistentHashDemoService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DemoApiController {

    private final ConsistentHashDemoService demoService;

    public DemoApiController(ConsistentHashDemoService demoService) {
        this.demoService = demoService;
    }

    @GetMapping("/dashboard")
    public ConsistentHashDemoService.DashboardState dashboard(
        @RequestParam(defaultValue = "6") int moduloServers,
        @RequestParam(defaultValue = "32") int replicas
    ) {
        return demoService.dashboard(moduloServers, replicas);
    }

    @GetMapping("/modulo")
    public ConsistentHashDemoService.ModuloScenario modulo() {
        return demoService.moduloScenario();
    }

    @PostMapping("/modulo/keys")
    public ConsistentHashDemoService.ModuloScenario addModuloKey() {
        return demoService.addModuloKey();
    }

    @PostMapping("/modulo/toggle")
    public ConsistentHashDemoService.ModuloScenario toggleModuloServer(@RequestParam String serverId) {
        return demoService.toggleModuloServer(serverId);
    }

    @PostMapping("/modulo/reset")
    public ConsistentHashDemoService.ModuloScenario resetModuloKeys() {
        return demoService.resetModulo();
    }

    @GetMapping("/ring")
    public ConsistentHashDemoService.RingScenario ring() {
        return demoService.steadyRing();
    }

    @GetMapping("/node-change")
    public ConsistentHashDemoService.RingScenario nodeChange() {
        return demoService.nodeScenario();
    }

    @PostMapping("/node-change/add")
    public ConsistentHashDemoService.RingScenario addNode() {
        return demoService.addNodeToCluster();
    }

    @PostMapping("/node-change/toggle")
    public ConsistentHashDemoService.RingScenario toggleNode(@RequestParam String serverId) {
        return demoService.toggleNodeInCluster(serverId);
    }

    @GetMapping("/virtual")
    public ConsistentHashDemoService.VirtualScenario virtualNodes(@RequestParam(defaultValue = "32") int replicas) {
        return demoService.virtualNodes(replicas);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
