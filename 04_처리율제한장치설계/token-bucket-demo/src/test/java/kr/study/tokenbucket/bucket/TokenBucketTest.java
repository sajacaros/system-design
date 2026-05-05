package kr.study.tokenbucket.bucket;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketTest {

    @Test
    void capacity_만큼_consume_성공_후_다음_호출은_실패한다() {
        TokenBucket bucket = new TokenBucket(3, 3);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
        assertThat(bucket.tokens()).isEqualTo(0);
    }

    @Test
    void 비어있을때_refill_후_consume이_가능하다() {
        TokenBucket bucket = new TokenBucket(3, 0);

        assertThat(bucket.tryConsume()).isFalse();

        bucket.refillOne();

        assertThat(bucket.tokens()).isEqualTo(1);
        assertThat(bucket.tryConsume()).isTrue();
    }

    @Test
    void 가득찬_상태에서_refill해도_capacity를_넘지_않는다() {
        TokenBucket bucket = new TokenBucket(3, 3);

        bucket.refillOne();
        bucket.refillOne();

        assertThat(bucket.tokens()).isEqualTo(3);
        assertThat(bucket.capacity()).isEqualTo(3);
    }
}
