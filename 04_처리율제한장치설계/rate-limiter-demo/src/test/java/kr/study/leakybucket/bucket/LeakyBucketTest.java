package kr.study.leakybucket.bucket;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LeakyBucketTest {

    @Test
    void capacity_만큼_enqueue_성공_후_다음은_실패한다() {
        LeakyBucket bucket = new LeakyBucket(3);

        assertThat(bucket.tryEnqueue("a")).isTrue();
        assertThat(bucket.tryEnqueue("b")).isTrue();
        assertThat(bucket.tryEnqueue("c")).isTrue();
        assertThat(bucket.tryEnqueue("d")).isFalse();
        assertThat(bucket.size()).isEqualTo(3);
    }

    @Test
    void enqueue_후_poll하면_FIFO_순서로_나온다() {
        LeakyBucket bucket = new LeakyBucket(3);
        bucket.tryEnqueue("a");
        bucket.tryEnqueue("b");
        bucket.tryEnqueue("c");

        assertThat(bucket.pollOne()).contains("a");
        assertThat(bucket.pollOne()).contains("b");
        assertThat(bucket.pollOne()).contains("c");
        assertThat(bucket.size()).isEqualTo(0);
    }

    @Test
    void 비어있는_큐_poll은_빈값을_반환한다() {
        LeakyBucket bucket = new LeakyBucket(3);

        Optional<String> result = bucket.pollOne();

        assertThat(result).isEmpty();
    }

    @Test
    void 가득_찬_상태에서_poll_후_다시_enqueue_가능하다() {
        LeakyBucket bucket = new LeakyBucket(2);
        bucket.tryEnqueue("a");
        bucket.tryEnqueue("b");
        assertThat(bucket.tryEnqueue("c")).isFalse();

        bucket.pollOne();

        assertThat(bucket.tryEnqueue("c")).isTrue();
        assertThat(bucket.size()).isEqualTo(2);
    }
}
