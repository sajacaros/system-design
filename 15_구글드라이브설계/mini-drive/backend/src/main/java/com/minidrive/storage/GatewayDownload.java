package com.minidrive.storage;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Builds the v1.6 gateway-download response: an empty-body 200 carrying only the
 * {@code X-Accel-Redirect}, {@code Content-Type} and {@code Content-Disposition} headers.
 *
 * <p>nginx consumes {@code X-Accel-Redirect} (it is NOT forwarded to the client) and streams
 * the object directly from MinIO. The backend never sees the bytes. The internal presign
 * (its SigV4 query) is therefore never exposed to the browser.
 * (storage-layout.md §"다운로드 모델 v1.6", api-contract.md §"계약 v1.6 정합 메모")
 */
@Component
public class GatewayDownload {

    /** nginx internal location prefix (fixed). storage-layout.md v1.6. */
    public static final String INTERNAL_PREFIX = "/_minio";
    /** Header consumed by nginx to trigger internal redirect. */
    public static final String X_ACCEL_REDIRECT = "X-Accel-Redirect";

    private final StorageService storage;
    private final StorageProperties props;

    public GatewayDownload(StorageService storage, StorageProperties props) {
        this.storage = storage;
        this.props = props;
    }

    /**
     * Build the empty-body 200 download response for the given object key + display filename.
     *
     * @param objectKey      MinIO key (e.g. {@code users/{userId}/{fileId}} or {@code versions/{fileId}/v{n}}).
     * @param downloadName   originalName used for Content-Type resolution and Content-Disposition.
     */
    public ResponseEntity<Void> redirect(String objectKey, String downloadName) {
        String contentType = ContentTypes.forFilename(downloadName);
        // Internal-only presign, ultra short-lived. The host/scheme are irrelevant: only the
        // path (bucket+key) and the SigV4 query are reused under /_minio/.
        Duration ttl = Duration.ofSeconds(props.getInternalPresignTtlSeconds());
        String presignedUrl = storage.presignGet(objectKey, ttl, contentType);
        String accelTarget = INTERNAL_PREFIX + pathAndQuery(presignedUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.set(X_ACCEL_REDIRECT, accelTarget);
        headers.set(HttpHeaders.CONTENT_TYPE, contentType);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + rfc5987(downloadName));
        return ResponseEntity.ok().headers(headers).build();
    }

    /** Extract {@code <path>?<query>} from a presigned URL (bucket+key path-style). */
    private static String pathAndQuery(String presignedUrl) {
        URI uri = URI.create(presignedUrl);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String query = uri.getRawQuery();
        return query == null ? path : path + "?" + query;
    }

    /**
     * RFC 5987 percent-encoding for the {@code filename*} parameter so UTF-8 (e.g. Korean)
     * file names survive. Encodes everything except the RFC 5987 attr-char set.
     */
    static String rfc5987(String name) {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if (isAttrChar(c)) {
                sb.append((char) c);
            } else {
                sb.append('%')
                        .append(Character.toUpperCase(Character.forDigit((c >> 4) & 0xF, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return sb.toString();
    }

    /** RFC 5987 attr-char = ALPHA / DIGIT / "!#$&+-.^_`|~". */
    private static boolean isAttrChar(int c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || "!#$&+-.^_`|~".indexOf(c) >= 0;
    }
}
