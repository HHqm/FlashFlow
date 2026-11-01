package com.flashflow.trade.outbox;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;

import com.flashflow.common.event.DomainEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxRepository repository;

    @Test
    void pollingModeKeepsRowsForThePublisher() {
        OutboxService service = new OutboxService(repository, JsonMapper.builder().build(), OutboxService.Mode.POLLING);

        service.append(event());

        verify(repository).saveAll(anyList());
        verify(repository, never()).deleteCapturedEvents(anyCollection());
    }

    @Test
    void cdcModeFlushesTheInsertBeforeDeletingItInTheSameTransaction() {
        OutboxService service = new OutboxService(repository, JsonMapper.builder().build(), OutboxService.Mode.CDC);

        service.append(event());

        InOrder sequence = inOrder(repository);
        sequence.verify(repository).saveAll(anyList());
        sequence.verify(repository).flush();
        sequence.verify(repository).deleteCapturedEvents(anyCollection());
    }

    private static DomainEvent event() {
        return DomainEvent.create("order.created.v1", "FF1", Map.of("orderNo", "FF1"));
    }
}
