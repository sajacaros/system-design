package com.minidrive.file;

import com.minidrive.auth.CurrentUser;
import com.minidrive.common.PageResponse;
import com.minidrive.file.FileService.VersionDownloadTarget;
import com.minidrive.file.dto.FileDtos.ContentUpdateResponse;
import com.minidrive.file.dto.FileDtos.FileDetailResponse;
import com.minidrive.file.dto.FileDtos.FileResponse;
import com.minidrive.file.dto.FileDtos.PatchRequest;
import com.minidrive.file.dto.FileDtos.RestoreRequest;
import com.minidrive.file.dto.FileDtos.RestoreResponse;
import com.minidrive.file.dto.FileDtos.VersionResponse;
import com.minidrive.storage.GatewayDownload;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final GatewayDownload gatewayDownload;

    public FileController(FileService fileService, GatewayDownload gatewayDownload) {
        this.fileService = fileService;
        this.gatewayDownload = gatewayDownload;
    }

    @GetMapping
    public PageResponse<FileResponse> list(
            @RequestParam(required = false) String folderId,
            @RequestParam(defaultValue = "UPLOADED") FileStatus status,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String extension,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // folderId is optional and accepts the literal "null" (contract: folderId={id|null}).
        // Unspecified / blank / "null" => root (null). Any other non-numeric => 400 VALIDATION.
        boolean folderSpecified = folderId != null && !folderId.isBlank()
                && !"null".equalsIgnoreCase(folderId.trim());
        Long parsedFolderId = null;
        if (folderSpecified) {
            try { // numeric folder id
                parsedFolderId = Long.parseLong(folderId.trim());
            } catch (NumberFormatException e) {
                throw new com.minidrive.common.ApiException(
                        com.minidrive.common.ErrorCode.VALIDATION,
                        "folderId must be a number or 'null'");
            }
        }
        return fileService.list(CurrentUser.id(), parsedFolderId,
                status, name, extension, sort, page, size);
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public FileResponse upload(@RequestParam("file") MultipartFile file,
                               @RequestParam(value = "folderId", required = false) Long folderId) {
        return fileService.upload(CurrentUser.id(), folderId, file);
    }

    @GetMapping("/{id}")
    public FileDetailResponse get(@PathVariable Long id) {
        return fileService.get(CurrentUser.id(), id);
    }

    /**
     * v1.6 gateway-download (authenticated). Re-validates owner + UPLOADED on every request,
     * then returns an empty-body 200 with X-Accel-Redirect; nginx streams from MinIO.
     * 401 UNAUTHENTICATED / 403 FORBIDDEN / 404 NOT_FOUND.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Void> download(@PathVariable Long id) {
        FileEntity file = fileService.resolveForDownload(CurrentUser.id(), id);
        return gatewayDownload.redirect(file.getObjectKey(), file.getOriginalName());
    }

    @PostMapping(value = "/{id}/content", consumes = "multipart/form-data")
    public ContentUpdateResponse uploadNewVersion(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "baseVersion", required = false) Integer baseVersion,
            @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite) {
        return fileService.uploadNewVersion(CurrentUser.id(), id, file, baseVersion, overwrite);
    }

    @PatchMapping("/{id}")
    public FileResponse patch(@PathVariable Long id, @Valid @RequestBody PatchRequest req) {
        return fileService.patch(CurrentUser.id(), id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        fileService.delete(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/versions")
    public List<VersionResponse> versions(@PathVariable Long id) {
        return fileService.listVersions(CurrentUser.id(), id);
    }

    /**
     * v1.6: this path is now the gateway download itself (no presigned-URL wrapper). Re-validates
     * owner per request, returns empty-body 200 + X-Accel-Redirect; nginx streams the version
     * object from MinIO. 401 / 403 / 404.
     */
    @GetMapping("/{id}/versions/{version}/download")
    public ResponseEntity<Void> versionDownload(@PathVariable Long id, @PathVariable int version) {
        VersionDownloadTarget target = fileService.resolveVersionForDownload(CurrentUser.id(), id, version);
        return gatewayDownload.redirect(target.objectKey(), target.originalName());
    }

    @PostMapping("/{id}/restore")
    public RestoreResponse restore(@PathVariable Long id,
                                   @RequestBody(required = false) RestoreRequest req) {
        Integer version = req == null ? null : req.version();
        return fileService.restore(CurrentUser.id(), id, version);
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> permanent(@PathVariable Long id) {
        fileService.permanentDelete(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
