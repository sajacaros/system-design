package kr.study.tokenbucket.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class BucketEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BucketEventPublisher.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        log.debug("emitter subscribed (total={})", emitters.size());
        return emitter;
    }

    public void sendInitial(SseEmitter emitter, int tokens, int capacity) {
        try {
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data(payload(tokens, capacity)));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
    }

    public void publish(String event, int tokens) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event)
                        .data("{\"tokens\":" + tokens + "}"));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    private String payload(int tokens, int capacity) {
        return "{\"tokens\":" + tokens + ",\"capacity\":" + capacity + "}";
    }
}
