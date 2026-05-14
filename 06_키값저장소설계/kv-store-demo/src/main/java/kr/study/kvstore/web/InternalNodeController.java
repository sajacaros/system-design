package kr.study.kvstore.web;

import kr.study.kvstore.domain.ClusterNodeService;
import kr.study.kvstore.domain.ClusterNodeService.AvailabilityCommand;
import kr.study.kvstore.domain.ClusterNodeService.GossipMessage;
import kr.study.kvstore.domain.ClusterNodeService.NodeSnapshot;
import kr.study.kvstore.domain.ClusterNodeService.QuorumSettingsCommand;
import kr.study.kvstore.domain.ClusterNodeService.ReplicaReadResponse;
import kr.study.kvstore.domain.ClusterNodeService.ReplicaWriteRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalNodeController {

    private final ClusterNodeService clusterNodeService;

    public InternalNodeController(ClusterNodeService clusterNodeService) {
        this.clusterNodeService = clusterNodeService;
    }

    @PostMapping("/replica/write")
    public void writeReplica(@RequestBody ReplicaWriteRequest request) {
        clusterNodeService.saveReplica(request);
    }

    @GetMapping("/replica/read/{key}")
    public ReplicaReadResponse readReplica(@PathVariable String key) {
        return clusterNodeService.readReplica(key);
    }

    @PostMapping("/gossip")
    public GossipMessage gossip(@RequestBody GossipMessage message) {
        return clusterNodeService.receiveGossip(message);
    }

    @PostMapping("/admin/availability")
    public void availability(@RequestBody AvailabilityCommand command) {
        clusterNodeService.setAvailable(command.available());
    }

    @PostMapping("/admin/quorum")
    public void quorum(@RequestBody QuorumSettingsCommand command) {
        clusterNodeService.applyQuorum(command);
    }

    @GetMapping("/snapshot")
    public NodeSnapshot snapshot() {
        return clusterNodeService.localSnapshot();
    }
}
