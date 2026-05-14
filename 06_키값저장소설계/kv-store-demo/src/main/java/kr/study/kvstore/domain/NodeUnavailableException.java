package kr.study.kvstore.domain;

public class NodeUnavailableException extends RuntimeException {

    public NodeUnavailableException(String message) {
        super(message);
    }
}
