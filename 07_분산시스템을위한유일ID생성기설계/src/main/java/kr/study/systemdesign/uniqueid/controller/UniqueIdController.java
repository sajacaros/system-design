package kr.study.systemdesign.uniqueid.controller;

import kr.study.systemdesign.uniqueid.service.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/unique-id")
public class UniqueIdController {
    @Autowired
    private SnowflakeIdGenerator idGenerator;

    @PostMapping(value="/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    public String generateId() {
        return String.valueOf(idGenerator.nextId());
    }
}
