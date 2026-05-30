package com.airasia.flight.cache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCoalescerTest {

    @Test
    void concurrentCallsForSameKeyRunLoaderOnce() throws Exception {
        RequestCoalescer coalescer = new RequestCoalescer();
        AtomicInteger loaderInvocations = new AtomicInteger();
        int concurrency = 50;

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger correctResults = new AtomicInteger();

        for (int i = 0; i < concurrency; i++) {
            pool.submit(() -> {
                ready.countDown();
                await(go);
                String value = coalescer.compute("route:2026-06", () -> {
                    loaderInvocations.incrementAndGet();
                    sleep(200); // hold long enough for all callers to coalesce
                    return "VALUE";
                });
                if ("VALUE".equals(value)) {
                    correctResults.incrementAndGet();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(loaderInvocations.get()).isEqualTo(1);
        assertThat(correctResults.get()).isEqualTo(concurrency);
    }

    @Test
    void differentKeysRunIndependently() {
        RequestCoalescer coalescer = new RequestCoalescer();
        assertThat(coalescer.compute("a", () -> 1)).isEqualTo(1);
        assertThat(coalescer.compute("b", () -> 2)).isEqualTo(2);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
