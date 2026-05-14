package kr.study.kvstore.web;

import kr.study.kvstore.domain.ClusterNodeService;
import kr.study.kvstore.domain.ClusterNodeService.AvailabilityCommand;
import kr.study.kvstore.domain.ClusterNodeService.AvailabilityResult;
import kr.study.kvstore.domain.ClusterNodeService.ClusterSnapshot;
import kr.study.kvstore.domain.ClusterNodeService.GossipResult;
import kr.study.kvstore.domain.ClusterNodeService.PutCommand;
import kr.study.kvstore.domain.ClusterNodeService.PutResult;
import kr.study.kvstore.domain.ClusterNodeService.ReadResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DemoApiController {

    private final ClusterNodeService clusterNodeService;

    public DemoApiController(ClusterNodeService clusterNodeService) {
        this.clusterNodeService = clusterNodeService;
    }

    @GetMapping("/state")
    public ClusterSnapshot state() {
        return clusterNodeService.clusterSnapshot();
    }

    @PostMapping("/kv")
    public PutResult put(@RequestBody PutCommand command) {
        return clusterNodeService.put(command);
    }

    @PostMapping("/nodes/{nodeId}/kv")
    public PutResult putOnNode(@PathVariable String nodeId, @RequestBody PutCommand command) {
        return clusterNodeService.putOnNode(nodeId, command);
    }

    @GetMapping("/kv/{key}")
    public ReadResult get(@PathVariable String key) {
        return clusterNodeService.get(key);
    }

    @GetMapping("/nodes/{nodeId}/kv/{key}")
    public ReadResult getFromNode(@PathVariable String nodeId, @PathVariable String key) {
        return clusterNodeService.getFromNode(nodeId, key);
    }

    @PostMapping("/gossip")
    public GossipResult gossip() {
        return clusterNodeService.gossipRound();
    }

    @PostMapping("/nodes/{nodeId}/availability")
    public AvailabilityResult availability(
        @PathVariable String nodeId,
        @RequestBody AvailabilityCommand command
    ) {
        return clusterNodeService.setNodeAvailability(nodeId, command.available());
    }
}
