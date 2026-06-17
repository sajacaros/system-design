package com.minidrive.common;

public class ApiException extends RuntimeException {
    private final ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(ErrorCode code) {
        this(code, code.name());
    }

    public ErrorCode getCode() {
        return code;
    }

    // Convenience factories
    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, message);
    }

    public static ApiException fileNotFound() {
        return new ApiException(ErrorCode.FILE_NOT_FOUND, "File not found");
    }

    public static ApiException folderNotFound() {
        return new ApiException(ErrorCode.FOLDER_NOT_FOUND, "Folder not found");
    }

    public static ApiException forbidden() {
        return new ApiException(ErrorCode.FORBIDDEN, "Not the owner of this resource");
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    public static ApiException nameConflict() {
        return new ApiException(ErrorCode.NAME_CONFLICT, "A resource with the same name already exists in this location");
    }

    public static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION, message);
    }
}
