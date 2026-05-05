package kr.study.fixedwindow.counter;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowCounterTest {

    private static class MutableClock extends Clock {
        private long millis;

        MutableClock(long millis) {
            this.millis = millis;
        }

        @Override public ZoneId getZone() { return ZoneId.systemDefault(); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public long millis() { return millis; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }

        void advance(long add) { this.millis += add; }
    }

    @Test
    void threshold_만큼_acquire_성공_후_같은_윈도우에서는_거부된다() {
        MutableClock clock = new MutableClock(0);
        FixedWindowCounter counter = new FixedWindowCounter(3, 10_000, clock);

        assertThat(counter.tryAcquire().admitted()).isTrue();
        assertThat(counter.tryAcquire().admitted()).isTrue();
        assertThat(counter.tryAcquire().admitted()).isTrue();
        assertThat(counter.tryAcquire().admitted()).isFalse();
    }

    @Test
    void 윈도우_경계를_넘으면_count가_초기화되고_newWindow가_true이다() {
        MutableClock clock = new MutableClock(0);
        FixedWindowCounter counter = new FixedWindowCounter(2, 10_000, clock);

        counter.tryAcquire();
        counter.tryAcquire();
        assertThat(counter.tryAcquire().admitted()).isFalse();

        clock.advance(10_000);
        FixedWindowCounter.AcquireResult r = counter.tryAcquire();

        assertThat(r.newWindow()).isTrue();
        assertThat(r.admitted()).isTrue();
        assertThat(r.count()).isEqualTo(1);
    }

    @Test
    void tick은_count를_변경하지_않는다() {
        MutableClock clock = new MutableClock(0);
        FixedWindowCounter counter = new FixedWindowCounter(2, 10_000, clock);
        counter.tryAcquire();

        FixedWindowCounter.AcquireResult t = counter.tick();

        assertThat(t.admitted()).isFalse();
        assertThat(t.count()).isEqualTo(1);
        assertThat(t.newWindow()).isFalse();
    }

    @Test
    void tick은_윈도우가_지났으면_newWindow를_true로_보고한다() {
        MutableClock clock = new MutableClock(0);
        FixedWindowCounter counter = new FixedWindowCounter(2, 10_000, clock);
        counter.tryAcquire();
        clock.advance(10_000);

        FixedWindowCounter.AcquireResult t = counter.tick();

        assertThat(t.newWindow()).isTrue();
        assertThat(t.count()).isEqualTo(0);
    }

    @Test
    void 윈도우_경계_근처에서_burst가_허용된다() {
        // 고정 윈도 카운터의 단점: 경계 부근에서 threshold의 2배까지 짧은 시간에 통과
        MutableClock clock = new MutableClock(0);
        FixedWindowCounter counter = new FixedWindowCounter(5, 10_000, clock);

        clock.advance(9_500);
        for (int i = 0; i < 5; i++) {
            assertThat(counter.tryAcquire().admitted()).isTrue();
        }

        clock.advance(1_000);
        for (int i = 0; i < 5; i++) {
            assertThat(counter.tryAcquire().admitted()).isTrue();
        }
    }
}
