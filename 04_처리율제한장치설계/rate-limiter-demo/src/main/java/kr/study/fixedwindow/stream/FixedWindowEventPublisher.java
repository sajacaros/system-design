package kr.study.fixedwindow.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class FixedWindowEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FixedWindowEventPublisher.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        log.debug("fixed-window emitter subscribed (total={})", emitters.size());
        return emitter;
    }

    public void sendInitial(SseEmitter emitter, long windowStart, int count, int threshold) {
        try {
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data(payload(windowStart, count, threshold)));
        } catch (IOException e) {
            emitters.remove(emitter);
            log.debug("fixed-window sendInitial failed, emitter removed", e);
        }
    }

    public void publish(String event, long windowStart, int count, int threshold) {
        String data = payload(windowStart, count, threshold);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException e) {
                emitters.remove(emitter);
                log.debug("fixed-window publish failed, emitter removed", e);
            }
        }
    }

    private String payload(long windowStart, int count, int threshold) {
        return "{\"windowStart\":" + windowStart
                + ",\"count\":" + count
                + ",\"threshold\":" + threshold + "}";
    }
}
