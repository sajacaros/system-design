package kr.study.slidingwindowlog.stream;

import kr.study.slidingwindowlog.log.SlidingWindowLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SlidingWindowLogEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowLogEventPublisher.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        log.debug("sliding-log emitter subscribed (total={})", emitters.size());
        return emitter;
    }

    public void sendInitial(SseEmitter emitter, SlidingWindowLog.Snapshot s) {
        try {
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data(payload(s.now(), s.size(), s.threshold(), s.windowSizeMillis(), s.entries())));
        } catch (IOException e) {
            emitters.remove(emitter);
            log.debug("sliding-log sendInitial failed, emitter removed", e);
        }
    }

    public void publish(String event, long now, int size, int threshold,
                        long windowSizeMillis, List<SlidingWindowLog.Entry> entries) {
        String data = payload(now, size, threshold, windowSizeMillis, entries);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException e) {
                emitters.remove(emitter);
                log.debug("sliding-log publish failed, emitter removed", e);
            }
        }
    }

    private String payload(long now, int size, int threshold, long windowSizeMillis,
                           List<SlidingWindowLog.Entry> entries) {
        StringJoiner arr = new StringJoiner(",", "[", "]");
        for (SlidingWindowLog.Entry e : entries) {
            arr.add("{\"ts\":" + e.ts() + ",\"accepted\":" + e.accepted() + "}");
        }
        return "{\"now\":" + now
                + ",\"size\":" + size
                + ",\"threshold\":" + threshold
                + ",\"windowMs\":" + windowSizeMillis
                + ",\"entries\":" + arr + "}";
    }
}
