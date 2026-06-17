package com.minidrive.notification;

import com.minidrive.auth.CurrentUser;
import com.minidrive.common.ApiException;
import com.minidrive.common.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository repository;

    public NotificationController(NotificationRepository repository) {
        this.repository = repository;
    }

    public record NotificationResponse(Long id, String type, String message, boolean isRead, String createdAt) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getMessage(), n.isRead(),
                    n.getCreatedAt() == null ? null : n.getCreatedAt().toString());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<NotificationResponse> list(@RequestParam(required = false, defaultValue = "false") boolean unread) {
        Long userId = CurrentUser.id();
        List<Notification> items = unread
                ? repository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId)
                : repository.findByUserIdOrderByCreatedAtDesc(userId);
        return items.stream().map(NotificationResponse::from).toList();
    }

    @PatchMapping("/{id}/read")
    @Transactional
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        Long userId = CurrentUser.id();
        Notification n = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Notification not found"));
        n.markRead();
        return ResponseEntity.noContent().build();
    }
}
