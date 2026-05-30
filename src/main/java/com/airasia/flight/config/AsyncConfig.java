package com.airasia.flight.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AsyncConfig {

    /**
     * Bounded platform-thread pool for the provider scatter-gather (Java 17).
     * Provider calls are I/O-bound and short-lived (hard-timed-out at ~450ms), and
     * the thundering-herd coalescing upstream means only a handful of route+month
     * rebuilds run at once — so a capped elastic pool fans out the 3 provider calls
     * cheaply. If the pool ever saturates, {@code CallerRunsPolicy} applies natural
     * back-pressure instead of unbounded thread growth.
     */
    @Bean(name = "providerExecutor", destroyMethod = "shutdown")
    public ExecutorService providerExecutor() {
        AtomicInteger counter = new AtomicInteger();
        return new ThreadPoolExecutor(16, 128, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "provider-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
