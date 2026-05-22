package kr.study.uniqueid.web;

import kr.study.uniqueid.domain.IdGeneratorService;
import kr.study.uniqueid.domain.IdGeneratorService.GenerateCommand;
import kr.study.uniqueid.domain.IdGeneratorService.NodeBatch;
import kr.study.uniqueid.domain.IdGeneratorService.NodeSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalNodeController {

    private final IdGeneratorService idGeneratorService;

    public InternalNodeController(IdGeneratorService idGeneratorService) {
        this.idGeneratorService = idGeneratorService;
    }

    @PostMapping("/generate")
    public NodeBatch generate(@RequestBody GenerateCommand command) {
        return idGeneratorService.generateLocal(command);
    }

    @GetMapping("/snapshot")
    public NodeSnapshot snapshot() {
        return idGeneratorService.localSnapshot();
    }
}
