package kr.study.urlshortener.domain;

import static org.assertj.core.api.Assertions.assertThat;

import kr.study.urlshortener.config.DemoProperties;
import kr.study.urlshortener.domain.UrlShortenerService.CollisionDemoResult;
import kr.study.urlshortener.domain.UrlShortenerService.ShortenCommand;
import kr.study.urlshortener.domain.UrlShortenerService.ShortenResult;
import org.junit.jupiter.api.Test;

class UrlShortenerServiceTest {

    private final UrlShortenerService service = new UrlShortenerService(
        new DemoProperties("http://localhost:7808", 1, 8, 7)
    );

    @Test
    void resolvesHashCollisionByRetryingWithSalt() {
        ShortenResult first = service.shorten(new ShortenCommand(
            "https://example.com/a",
            GenerationStrategy.HASH,
            "abc123Z"
        ));
        ShortenResult second = service.shorten(new ShortenCommand(
            "https://example.com/b",
            GenerationStrategy.HASH,
            "abc123Z"
        ));

        assertThat(first.mapping().code()).isEqualTo("abc123Z");
        assertThat(second.mapping().code()).isNotEqualTo("abc123Z");
        assertThat(second.mapping().hashAttempt()).isEqualTo(1);
        assertThat(second.collisionSteps()).hasSize(1);
        assertThat(second.collisionSteps().get(0).occupiedBy()).isEqualTo("https://example.com/a");
    }

    @Test
    void generatesSnowflakeIdAndEncodesWithBase62() {
        ShortenResult result = service.shorten(new ShortenCommand(
            "https://example.com/id",
            GenerationStrategy.ID_BASE62,
            null
        ));

        assertThat(result.mapping().numericId()).isNotBlank();
        assertThat(result.mapping().code()).matches("[0-9a-zA-Z]+");
        assertThat(result.mapping().strategy()).isEqualTo(GenerationStrategy.ID_BASE62);
        assertThat(result.mapping().workerId()).isEqualTo(8);
    }

    @Test
    void collisionDemoCreatesOneCollisionStep() {
        CollisionDemoResult result = service.runCollisionDemo(new ShortenCommand(
            "https://example.com/requested",
            GenerationStrategy.HASH,
            null
        ));

        assertThat(result.first().mapping().code()).isEqualTo(result.forcedCode());
        assertThat(result.second().mapping().longUrl()).isEqualTo("https://example.com/requested");
        assertThat(result.second().collisionSteps()).isNotEmpty();
    }
}
