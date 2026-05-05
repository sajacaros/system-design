package kr.study.leakybucket.ratelimit;

public sealed interface Outcome {

    record Accepted(String reqId) implements Outcome {}

    record Rejected() implements Outcome {}
}
