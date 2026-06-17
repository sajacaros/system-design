package com.minidrive.storage;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves a response Content-Type from a filename/extension.
 *
 * <p>Text-based formats (txt, csv, json, xml, md, html, log, ...) are served with an
 * explicit {@code ; charset=UTF-8} so browsers render UTF-8 (e.g. Korean) correctly
 * instead of falling back to a platform default charset under {@code nosniff}.
 * Everything else gets a conservative binary-safe MIME (often {@code application/octet-stream}).
 */
public final class ContentTypes {

    private static final String UTF8 = "; charset=UTF-8";

    /** Extensions treated as UTF-8 text. Kept conservative to avoid mangling binaries. */
    private static final Map<String, String> TEXT_MIME = Map.ofEntries(
            Map.entry("txt", "text/plain"),
            Map.entry("log", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("markdown", "text/markdown"),
            Map.entry("csv", "text/csv"),
            Map.entry("tsv", "text/tab-separated-values"),
            Map.entry("json", "application/json"),
            Map.entry("xml", "application/xml"),
            Map.entry("yaml", "text/yaml"),
            Map.entry("yml", "text/yaml"),
            Map.entry("html", "text/html"),
            Map.entry("htm", "text/html"),
            Map.entry("css", "text/css"),
            Map.entry("js", "text/javascript"),
            Map.entry("ts", "text/plain"),
            Map.entry("sql", "text/plain"),
            Map.entry("ini", "text/plain"),
            Map.entry("conf", "text/plain"),
            Map.entry("properties", "text/plain"),
            Map.entry("sh", "text/plain"),
            Map.entry("py", "text/plain"),
            Map.entry("java", "text/plain"),
            Map.entry("svg", "image/svg+xml"));

    /** Common binary MIMEs so downloads are reasonable; default is octet-stream. */
    private static final Map<String, String> BINARY_MIME = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("zip", "application/zip"),
            Map.entry("gz", "application/gzip"),
            Map.entry("tar", "application/x-tar"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"));

    private ContentTypes() {
    }

    /** Whether the given extension (lower-case, no dot) is treated as UTF-8 text. */
    public static boolean isText(String ext) {
        return ext != null && TEXT_MIME.containsKey(ext.toLowerCase(Locale.ROOT));
    }

    public static Set<String> textExtensions() {
        return TEXT_MIME.keySet();
    }

    /**
     * Resolve a response Content-Type from a filename. Text types always carry
     * {@code ; charset=UTF-8}; unknown types fall back to {@code application/octet-stream}.
     */
    public static String forFilename(String filename) {
        return forExtension(extensionOf(filename));
    }

    /** Resolve from a bare extension (lower-case, no dot). */
    public static String forExtension(String ext) {
        if (ext == null) {
            return "application/octet-stream";
        }
        String key = ext.toLowerCase(Locale.ROOT);
        String textMime = TEXT_MIME.get(key);
        if (textMime != null) {
            // svg is binary-ish but XML text; still safe to add charset.
            return textMime + UTF8;
        }
        return BINARY_MIME.getOrDefault(key, "application/octet-stream");
    }

    /**
     * Normalize a client-supplied content-type for storage: if it is a text type
     * without a charset, append {@code ; charset=UTF-8}; otherwise return as-is.
     * Used when persisting the object so its stored Content-Type is sane even
     * before any presign override.
     */
    public static String normalizeForStorage(String clientContentType, String filename) {
        String ext = extensionOf(filename);
        if (isText(ext)) {
            if (clientContentType == null || clientContentType.isBlank()) {
                return forExtension(ext);
            }
            String lower = clientContentType.toLowerCase(Locale.ROOT);
            if (lower.contains("charset=")) {
                return clientContentType;
            }
            return clientContentType + UTF8;
        }
        if (clientContentType == null || clientContentType.isBlank()) {
            return forExtension(ext);
        }
        return clientContentType;
    }

    private static String extensionOf(String name) {
        if (name == null) {
            return null;
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
