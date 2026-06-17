package com.minidrive.sync;

import com.minidrive.notification.Notification;
import com.minidrive.notification.NotificationRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes a STOMP event to the owner's personal queue AND persists a notification row
 * (websocket-events.md §발행 시점). convertAndSendToUser delivers to all of the user's
 * connections (multi-device sync).
 */
@Service
public class SyncEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRepository notificationRepository;

    public SyncEventPublisher(SimpMessagingTemplate messagingTemplate,
                              NotificationRepository notificationRepository) {
        this.messagingTemplate = messagingTemplate;
        this.notificationRepository = notificationRepository;
    }

    public void publish(Long ownerId, SyncEvent event, String message) {
        notificationRepository.save(new Notification(ownerId, event.type(), message));
        messagingTemplate.convertAndSendToUser(
                String.valueOf(ownerId), "/queue/notifications", event);
    }
}
