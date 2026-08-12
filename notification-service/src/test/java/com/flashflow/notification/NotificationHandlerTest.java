package com.flashflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import com.flashflow.common.event.DomainEvent;
import com.flashflow.common.event.EventTypes;
import com.flashflow.notification.message.NotificationEntity;
import com.flashflow.notification.message.NotificationHandler;
import com.flashflow.notification.message.NotificationRepository;
import com.flashflow.notification.message.ProcessedMessageRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationHandlerTest {

    @Mock
    private ProcessedMessageRepository processedMessageRepository;

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new NotificationHandler(
                processedMessageRepository,
                notificationRepository,
                new SimpleMeterRegistry());
    }

    @Test
    void storesNotificationAndInboxMarkerInOneHandlerCall() {
        DomainEvent event = DomainEvent.create(
                EventTypes.ORDER_CREATED,
                "FF-100",
                Map.of("orderNo", "FF-100", "userId", 42L));
        when(processedMessageRepository.insertIfAbsent(
                org.mockito.ArgumentMatchers.eq(event.eventId()),
                org.mockito.ArgumentMatchers.eq(event.eventType()),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);

        handler.handle(event);

        var notificationCaptor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getUserId()).isEqualTo(42L);
        assertThat(notificationCaptor.getValue().getTemplateCode()).isEqualTo("ORDER_CREATED");
        assertThat(notificationCaptor.getValue().getContent()).contains("FF-100");
    }

    @Test
    void ignoresAlreadyProcessedEvent() {
        UUID eventId = UUID.randomUUID();
        DomainEvent event = new DomainEvent(
                eventId,
                EventTypes.ORDER_PAID,
                "FF-101",
                java.time.Instant.now(),
                1,
                Map.of("orderNo", "FF-101", "userId", 42L));
        when(processedMessageRepository.insertIfAbsent(
                org.mockito.ArgumentMatchers.eq(eventId),
                org.mockito.ArgumentMatchers.eq(event.eventType()),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(0);

        handler.handle(event);

        verify(notificationRepository, never()).save(
                org.mockito.ArgumentMatchers.any(NotificationEntity.class));
    }

    @Test
    void createsShipmentNotificationWithTrackingInformation() {
        DomainEvent event = DomainEvent.create(
                EventTypes.ORDER_SHIPPED,
                "FF-102",
                Map.of(
                        "orderNo", "FF-102",
                        "userId", 42L,
                        "carrier", "SF Express",
                        "trackingNo", "SF123456"));
        when(processedMessageRepository.insertIfAbsent(
                org.mockito.ArgumentMatchers.eq(event.eventId()),
                org.mockito.ArgumentMatchers.eq(event.eventType()),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);

        handler.handle(event);

        var captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getTemplateCode()).isEqualTo("ORDER_SHIPPED");
        assertThat(captor.getValue().getContent())
                .contains("FF-102", "SF Express", "SF123456");
    }
}
