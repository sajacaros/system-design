package com.minidrive.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentTypesTest {

    @Test
    void textExtensions_getUtf8Charset() {
        assertThat(ContentTypes.forFilename("notes.txt")).isEqualTo("text/plain; charset=UTF-8");
        assertThat(ContentTypes.forFilename("data.csv")).isEqualTo("text/csv; charset=UTF-8");
        assertThat(ContentTypes.forFilename("config.json")).isEqualTo("application/json; charset=UTF-8");
        assertThat(ContentTypes.forFilename("doc.md")).isEqualTo("text/markdown; charset=UTF-8");
        assertThat(ContentTypes.forFilename("page.html")).isEqualTo("text/html; charset=UTF-8");
        assertThat(ContentTypes.forFilename("app.log")).contains("charset=UTF-8");
        assertThat(ContentTypes.forFilename("MixedCase.TXT")).isEqualTo("text/plain; charset=UTF-8");
    }

    @Test
    void binaryExtensions_haveNoCharset() {
        assertThat(ContentTypes.forFilename("photo.png")).isEqualTo("image/png");
        assertThat(ContentTypes.forFilename("archive.zip")).isEqualTo("application/zip");
        assertThat(ContentTypes.forFilename("report.pdf")).isEqualTo("application/pdf");
        assertThat(ContentTypes.forFilename("photo.png")).doesNotContain("charset");
    }

    @Test
    void unknownOrNoExtension_fallsBackToOctetStream() {
        assertThat(ContentTypes.forFilename("mystery.xyz")).isEqualTo("application/octet-stream");
        assertThat(ContentTypes.forFilename("noext")).isEqualTo("application/octet-stream");
        assertThat(ContentTypes.forFilename(null)).isEqualTo("application/octet-stream");
    }

    @Test
    void normalizeForStorage_addsCharsetToTextWithoutOne() {
        assertThat(ContentTypes.normalizeForStorage("text/plain", "a.txt"))
                .isEqualTo("text/plain; charset=UTF-8");
        // already has charset -> untouched
        assertThat(ContentTypes.normalizeForStorage("text/plain; charset=UTF-8", "a.txt"))
                .isEqualTo("text/plain; charset=UTF-8");
        // missing client type -> resolved from extension
        assertThat(ContentTypes.normalizeForStorage(null, "a.txt"))
                .isEqualTo("text/plain; charset=UTF-8");
        // binary client type preserved as-is
        assertThat(ContentTypes.normalizeForStorage("image/png", "a.png"))
                .isEqualTo("image/png");
    }
}
