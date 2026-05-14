package kr.study.kvstore.web;

import java.util.Map;
import kr.study.kvstore.domain.NodeUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NodeUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> nodeUnavailable(NodeUnavailableException exception) {
        return Map.of("error", exception.getMessage());
    }
}
