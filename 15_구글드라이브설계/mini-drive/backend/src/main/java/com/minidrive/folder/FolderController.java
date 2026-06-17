package com.minidrive.folder;

import com.minidrive.auth.CurrentUser;
import com.minidrive.folder.dto.FolderDtos.CreateRequest;
import com.minidrive.folder.dto.FolderDtos.FolderResponse;
import com.minidrive.folder.dto.FolderDtos.UpdateRequest;
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

import java.util.List;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping
    public List<FolderResponse> list(@RequestParam(required = false) Long parentId) {
        return folderService.list(CurrentUser.id(), parentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FolderResponse create(@Valid @RequestBody CreateRequest req) {
        return folderService.create(CurrentUser.id(), req);
    }

    @PatchMapping("/{id}")
    public FolderResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
        return folderService.update(CurrentUser.id(), id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        folderService.delete(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
