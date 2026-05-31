package kr.study.urlshortener.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Base62Test {

    @Test
    void encodeUsesBase62Alphabet() {
        assertThat(Base62.encode(0)).isEqualTo("0");
        assertThat(Base62.encode(61)).isEqualTo("Z");
        assertThat(Base62.encode(62)).isEqualTo("10");
    }

    @Test
    void fixedEncodingKeepsRequestedLength() {
        assertThat(Base62.encodeFixed(new byte[] {1, 2, 3}, 7)).hasSize(7);
    }
}
