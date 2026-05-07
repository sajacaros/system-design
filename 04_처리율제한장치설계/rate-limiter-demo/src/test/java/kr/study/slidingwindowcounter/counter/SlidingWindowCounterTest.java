package kr.study.slidingwindowcounter.counter;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowCounterTest {

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
    void 책_예시_3_더하기_5곱하기_0_7은_6_5_이므로_threshold_7이면_통과() {
        MutableClock clock = new MutableClock(10_000);                  // 윈도 시작
        SlidingWindowCounter counter = new SlidingWindowCounter(7, 10_000, clock);

        for (int i = 0; i < 5; i++) counter.tryAcquire();               // prev에 채울 5건
        clock.advance(10_000);                                          // 새 윈도 시작 (prev=5, curr=0)
        clock.advance(3_000);                                           // 윈도의 30% 경과 → prevWeight=0.7
        for (int i = 0; i < 3; i++) counter.tryAcquire();               // 0+3.5,1+3.5,2+3.5 → 모두 통과, curr=3

        SlidingWindowCounter.AcquireResult r = counter.tryAcquire();
        assertThat(r.prevCount()).isEqualTo(5);
        assertThat(r.prevWeight()).isCloseTo(0.7, org.assertj.core.data.Offset.offset(1e-9));
        // 직전 weighted = 3 + 5*0.7 = 6.5 < 7 → 통과 → curr=4 → weighted = 4 + 5*0.7 = 7.5
        assertThat(r.admitted()).isTrue();
        assertThat(r.currCount()).isEqualTo(4);
        assertThat(r.weighted()).isCloseTo(7.5, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void weighted가_threshold_이상이면_거부() {
        MutableClock clock = new MutableClock(10_000);
        SlidingWindowCounter counter = new SlidingWindowCounter(7, 10_000, clock);

        for (int i = 0; i < 5; i++) counter.tryAcquire();               // prev=5
        clock.advance(10_000);
        clock.advance(3_000);                                           // prevWeight=0.7
        for (int i = 0; i < 4; i++) counter.tryAcquire();               // curr=4 (책 예시 다음 요청까지 진행)

        // weighted = 4 + 5*0.7 = 7.5 ≥ 7 → 거부
        SlidingWindowCounter.AcquireResult r = counter.tryAcquire();
        assertThat(r.admitted()).isFalse();
        assertThat(r.currCount()).isEqualTo(4);
    }

    @Test
    void 한_윈도를_건너뛰면_prev는_0() {
        MutableClock clock = new MutableClock(10_000);
        SlidingWindowCounter counter = new SlidingWindowCounter(7, 10_000, clock);

        for (int i = 0; i < 5; i++) counter.tryAcquire();
        clock.advance(25_000);                                          // 두 윈도 이상 점프

        SlidingWindowCounter.AcquireResult r = counter.tryAcquire();
        assertThat(r.prevCount()).isZero();
        assertThat(r.currCount()).isEqualTo(1);
        assertThat(r.admitted()).isTrue();
    }

    @Test
    void 윈도_시작_직후는_prevWeight_거의_1() {
        MutableClock clock = new MutableClock(10_000);
        SlidingWindowCounter counter = new SlidingWindowCounter(7, 10_000, clock);

        for (int i = 0; i < 7; i++) counter.tryAcquire();               // prev=7로 한도까지
        clock.advance(10_000);                                          // 새 윈도 (prev=7, curr=0)

        // 새 윈도 진입 직후 prevWeight ≈ 1 → weighted = 0 + 7*1 = 7 → 거부
        SlidingWindowCounter.AcquireResult r = counter.tryAcquire();
        assertThat(r.admitted()).isFalse();
        assertThat(r.prevWeight()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void 윈도_끝날_무렵에는_prevWeight_거의_0_이라_curr만_고려() {
        MutableClock clock = new MutableClock(10_000);
        SlidingWindowCounter counter = new SlidingWindowCounter(7, 10_000, clock);

        for (int i = 0; i < 7; i++) counter.tryAcquire();               // prev=7
        clock.advance(10_000);
        clock.advance(9_000);                                           // 90% 경과 → prevWeight=0.1

        // weighted = 0 + 7*0.1 = 0.7 → 통과 가능
        SlidingWindowCounter.AcquireResult r = counter.tryAcquire();
        assertThat(r.admitted()).isTrue();
        assertThat(r.prevWeight()).isCloseTo(0.1, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void snapshot은_상태를_변경하지_않는다() {
        MutableClock clock = new MutableClock(10_000);
        SlidingWindowCounter counter = new SlidingWindowCounter(7, 10_000, clock);

        for (int i = 0; i < 3; i++) counter.tryAcquire();
        SlidingWindowCounter.Snapshot s1 = counter.snapshot();
        SlidingWindowCounter.Snapshot s2 = counter.snapshot();

        assertThat(s1.currCount()).isEqualTo(3);
        assertThat(s2.currCount()).isEqualTo(3);
    }
}
