package kr.study.consistenthash.web;

import java.util.Map;
import kr.study.consistenthash.domain.ConsistentHashDemoService;
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
        @RequestParam(defaultValue = "1") int moduloServers
    ) {
        return demoService.dashboard(moduloServers);
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

    @GetMapping("/hashring")
    public ConsistentHashDemoService.HashRingScenario hashRing() {
        return demoService.hashRing();
    }

    @PostMapping("/hashring/keys")
    public ConsistentHashDemoService.HashRingScenario addHashRingKey() {
        return demoService.addHashRingKey();
    }

    @PostMapping("/hashring/reset")
    public ConsistentHashDemoService.HashRingScenario resetHashRing() {
        return demoService.resetHashRing();
    }

    @PostMapping("/hashring/servers/toggle")
    public ConsistentHashDemoService.HashRingScenario toggleHashRingServer(@RequestParam String serverId) {
        return demoService.toggleHashRingServer(serverId);
    }

    @GetMapping("/virtual")
    public ConsistentHashDemoService.VirtualNodesScenario virtualNodes() {
        return demoService.virtualNodes();
    }

    @PostMapping("/virtual/keys")
    public ConsistentHashDemoService.VirtualNodesScenario addVirtualNodeKey() {
        return demoService.addVirtualNodeKey();
    }

    @PostMapping("/virtual/replicas")
    public ConsistentHashDemoService.VirtualNodesScenario updateVirtualReplicas(@RequestParam int replicas) {
        return demoService.updateVirtualReplicas(replicas);
    }

    @PostMapping("/virtual/servers/toggle")
    public ConsistentHashDemoService.VirtualNodesScenario toggleVirtualNodeServer(@RequestParam String serverId) {
        return demoService.toggleVirtualNodeServer(serverId);
    }

    @PostMapping("/virtual/reset")
    public ConsistentHashDemoService.VirtualNodesScenario resetVirtualNodes() {
        return demoService.resetVirtualNodes();
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
