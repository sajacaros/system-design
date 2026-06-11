package kr.study.autocomplete.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AutocompleteApiControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void compare_api는_두_엔진의_결과를_함께_반환한다() {
        URI uri = UriComponentsBuilder.fromPath("/api/autocomplete/compare")
                .queryParam("prefix", "한")
                .queryParam("limit", 5)
                .build()
                .encode()
                .toUri();

        ResponseEntity<String> response = restTemplate.getForEntity(
                uri,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("직접 구현 트라이")
                .contains("korean-utils")
                .contains("한국");
    }
}
