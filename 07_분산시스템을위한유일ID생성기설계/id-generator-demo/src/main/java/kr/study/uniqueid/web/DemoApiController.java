package kr.study.uniqueid.web;

import kr.study.uniqueid.domain.IdGeneratorService;
import kr.study.uniqueid.domain.IdGeneratorService.ClusterGenerateCommand;
import kr.study.uniqueid.domain.IdGeneratorService.ClusterGenerateResult;
import kr.study.uniqueid.domain.IdGeneratorService.ClusterSnapshot;
import kr.study.uniqueid.domain.IdGeneratorService.GenerateCommand;
import kr.study.uniqueid.domain.IdGeneratorService.NodeBatch;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DemoApiController {

    private final IdGeneratorService idGeneratorService;

    public DemoApiController(IdGeneratorService idGeneratorService) {
        this.idGeneratorService = idGeneratorService;
    }

    @GetMapping("/state")
    public ClusterSnapshot state() {
        return idGeneratorService.clusterSnapshot();
    }

    @PostMapping("/generate-local")
    public NodeBatch generateLocal(@RequestBody GenerateCommand command) {
        return idGeneratorService.generateLocal(command);
    }

    @PostMapping("/generate-cluster")
    public ClusterGenerateResult generateCluster(@RequestBody ClusterGenerateCommand command) {
        return idGeneratorService.generateCluster(command);
    }
}
