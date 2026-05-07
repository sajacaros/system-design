package kr.study.slidingwindowcounter.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SlidingWindowCounterEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowCounterEventPublisher.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        log.debug("sliding-counter emitter subscribed (total={})", emitters.size());
        return emitter;
    }

    public void sendInitial(SseEmitter emitter, long now, long currWindowStartMillis,
                            int prevCount, int currCount, double weighted, double prevWeight,
                            int threshold, long windowSizeMillis) {
        try {
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data(payload(now, currWindowStartMillis, prevCount, currCount,
                            weighted, prevWeight, threshold, windowSizeMillis)));
        } catch (IOException e) {
            emitters.remove(emitter);
            log.debug("sliding-counter sendInitial failed, emitter removed", e);
        }
    }

    public void publish(String event, long now, long currWindowStartMillis,
                        int prevCount, int currCount, double weighted, double prevWeight,
                        int threshold, long windowSizeMillis) {
        String data = payload(now, currWindowStartMillis, prevCount, currCount,
                weighted, prevWeight, threshold, windowSizeMillis);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException e) {
                emitters.remove(emitter);
                log.debug("sliding-counter publish failed, emitter removed", e);
            }
        }
    }

    private String payload(long now, long currWindowStartMillis,
                           int prevCount, int currCount, double weighted, double prevWeight,
                           int threshold, long windowSizeMillis) {
        return "{\"now\":" + now
                + ",\"currWindowStart\":" + currWindowStartMillis
                + ",\"prevCount\":" + prevCount
                + ",\"currCount\":" + currCount
                + ",\"weighted\":" + String.format(java.util.Locale.ROOT, "%.4f", weighted)
                + ",\"prevWeight\":" + String.format(java.util.Locale.ROOT, "%.4f", prevWeight)
                + ",\"threshold\":" + threshold
                + ",\"windowMs\":" + windowSizeMillis + "}";
    }
}
