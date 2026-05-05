package kr.study.leakybucket.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class LeakyEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LeakyEventPublisher.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        log.debug("leaky emitter subscribed (total={})", emitters.size());
        return emitter;
    }

    public void sendInitial(SseEmitter emitter, int size, int capacity) {
        try {
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data(payload(null, size, capacity)));
        } catch (IOException e) {
            emitters.remove(emitter);
            log.debug("leaky sendInitial failed, emitter removed", e);
        }
    }

    public void publish(String event, String reqId, int size, int capacity) {
        String data = payload(reqId, size, capacity);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException e) {
                emitters.remove(emitter);
                log.debug("leaky publish failed, emitter removed", e);
            }
        }
    }

    private String payload(String reqId, int size, int capacity) {
        StringBuilder sb = new StringBuilder("{\"size\":").append(size)
                .append(",\"capacity\":").append(capacity);
        if (reqId != null) {
            sb.append(",\"reqId\":\"").append(reqId).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }
}
