package kr.study.slidingwindowlog.log;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowLogTest {

    private static class MutableClock extends Clock {
        private long millis;

        MutableClock(long millis) { this.millis = millis; }

        @Override public ZoneId getZone() { return ZoneId.systemDefault(); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public long millis() { return millis; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }

        void advance(long add) { this.millis += add; }
    }

    @Test
    void threshold_까지는_admitted_이후는_rejected() {
        MutableClock clock = new MutableClock(1_000);
        SlidingWindowLog log = new SlidingWindowLog(5, 10_000, clock);

        for (int i = 0; i < 5; i++) {
            assertThat(log.tryAcquire().admitted()).isTrue();
        }
        assertThat(log.tryAcquire().admitted()).isFalse();
    }

    @Test
    void 거부된_요청도_로그에_보관된다() {
        MutableClock clock = new MutableClock(1_000);
        SlidingWindowLog log = new SlidingWindowLog(5, 10_000, clock);

        for (int i = 0; i < 5; i++) log.tryAcquire();
        SlidingWindowLog.AcquireResult rejected = log.tryAcquire();

        assertThat(rejected.admitted()).isFalse();
        assertThat(rejected.size()).isEqualTo(6);
        assertThat(rejected.entries()).hasSize(6);
        assertThat(rejected.entries().get(5).accepted()).isFalse();
    }

    @Test
    void 윈도우_바깥의_엔트리는_제거된다() {
        MutableClock clock = new MutableClock(1_000);
        SlidingWindowLog log = new SlidingWindowLog(5, 10_000, clock);

        for (int i = 0; i < 5; i++) log.tryAcquire();
        clock.advance(10_001);

        SlidingWindowLog.AcquireResult next = log.tryAcquire();
        assertThat(next.admitted()).isTrue();
        assertThat(next.size()).isEqualTo(1);
    }

    @Test
    void 거부된_타임스탬프가_다음_요청을_계속_거부시킨다() {
        // 책의 단점 - 한 번 한도 초과되면 거부 entry도 한도를 차지
        MutableClock clock = new MutableClock(1_000);
        SlidingWindowLog log = new SlidingWindowLog(5, 10_000, clock);

        for (int i = 0; i < 5; i++) log.tryAcquire();              // ●●●●● at t=1000
        clock.advance(1_000);
        for (int i = 0; i < 3; i++) {                              // ✕✕✕ at t=2000
            assertThat(log.tryAcquire().admitted()).isFalse();
        }

        clock.advance(9_500);                                       // t=12500: 1000 entries 만료
        // 윈도 안에는 t=2000 시점의 ✕ 3개만 남음 → size=3 → 다음은 통과
        SlidingWindowLog.AcquireResult ok = log.tryAcquire();
        assertThat(ok.admitted()).isTrue();
        assertThat(ok.size()).isEqualTo(4);
    }

    @Test
    void snapshot_은_윈도_안_엔트리만_반환한다() {
        MutableClock clock = new MutableClock(1_000);
        SlidingWindowLog log = new SlidingWindowLog(5, 10_000, clock);

        log.tryAcquire();
        clock.advance(10_001);
        SlidingWindowLog.Snapshot s = log.snapshot();

        assertThat(s.size()).isZero();
        assertThat(s.entries()).isEmpty();
    }

    @Test
    void 엔트리는_도착순서를_유지한다() {
        MutableClock clock = new MutableClock(1_000);
        SlidingWindowLog log = new SlidingWindowLog(3, 10_000, clock);

        log.tryAcquire();
        clock.advance(100);
        log.tryAcquire();
        clock.advance(100);
        log.tryAcquire();
        clock.advance(100);
        log.tryAcquire(); // rejected

        SlidingWindowLog.Snapshot s = log.snapshot();
        assertThat(s.entries()).extracting(SlidingWindowLog.Entry::accepted)
                .containsExactly(true, true, true, false);
    }
}
