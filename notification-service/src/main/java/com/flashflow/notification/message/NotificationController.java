package com.flashflow.notification.message;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    List<NotificationView> list(@RequestParam long userId) {
        return notificationRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationView::from)
                .toList();
    }

    record NotificationView(
            long id,
            UUID eventId,
            String channel,
            String templateCode,
            String content,
            String status,
            Instant createdAt
    ) {
        static NotificationView from(NotificationEntity entity) {
            return new NotificationView(
                    entity.getId(),
                    entity.getEventId(),
                    entity.getChannel(),
                    entity.getTemplateCode(),
                    entity.getContent(),
                    entity.getStatus(),
                    entity.getCreatedAt());
        }
    }
}
