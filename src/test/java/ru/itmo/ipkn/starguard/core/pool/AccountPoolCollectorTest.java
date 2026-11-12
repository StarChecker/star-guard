package ru.itmo.ipkn.starguard.core.pool;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import ru.itmo.ipkn.starguard.core.config.GithubProperties;
import ru.itmo.ipkn.starguard.core.github.GithubAccountPoolClient;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static ru.itmo.ipkn.starguard.core.pool.PoolFixtures.*;

class AccountPoolCollectorTest {
    private final AccountPoolStore store = mock(AccountPoolStore.class);
    private final GithubAccountPoolClient client = mock(GithubAccountPoolClient.class);
    private final AccountPoolCollector collector = new AccountPoolCollector(store, client, defaults(),
            GithubProperties.defaults(), mock(TaskScheduler.class), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void refreshesOldAccountsBeforeDiscoveringAndPersistsCursor() {
        when(client.hasBudget(anyInt(), any())).thenReturn(true);
        when(store.due(any(), any(), anyInt())).thenReturn(List.of("old"));
        when(client.profile("old")).thenReturn(Optional.empty());
        var seed = new AccountPoolStore.Seed("R1", null);
        when(store.nextSeed(NOW)).thenReturn(Optional.of(seed));
        when(client.forks("R1", null, 20)).thenReturn(new GithubAccountPoolClient.ForkPage(List.of("new"), "next"));
        when(client.profile("new")).thenReturn(Optional.of(bursty("new")));
        collector.collect();
        var order = inOrder(client, store);
        order.verify(client).profile("old");
        order.verify(store).save("old", 0, List.of(), NOW, 10000);
        order.verify(client).forks("R1", null, 20);
        order.verify(store).save(eq("new"), eq(1.0), anyList(), eq(NOW), eq(10000));
        order.verify(store).advance(seed, "next", NOW);
    }

    @Test
    void doesNotAdvancePageWhenBudgetRunsOut() {
        when(store.due(any(), any(), anyInt())).thenReturn(List.of());
        when(client.hasBudget(anyInt(), any())).thenReturn(true, false);
        var seed = new AccountPoolStore.Seed("R1", "old-cursor");
        when(store.nextSeed(NOW)).thenReturn(Optional.of(seed));
        when(client.forks("R1", "old-cursor", 20))
                .thenReturn(new GithubAccountPoolClient.ForkPage(List.of("new"), "next"));
        collector.collect();
        verify(client, never()).profile(anyString());
        verify(store, never()).advance(any(), any(), any());
    }

    @Test
    void failedProfileIsDeferredWithoutOverwritingEvidence() {
        when(store.due(any(), any(), anyInt())).thenReturn(List.of("old"));
        when(client.hasBudget(anyInt(), any())).thenReturn(true);
        when(client.profile("old")).thenThrow(new IllegalStateException("offline"));
        assertThatThrownBy(collector::collect).isInstanceOf(IllegalStateException.class);
        verify(store).defer("old", NOW.plus(defaults().refreshAfter()));
        verify(store, never()).save(anyString(), anyDouble(), anyList(), any(), anyInt());
    }
}
