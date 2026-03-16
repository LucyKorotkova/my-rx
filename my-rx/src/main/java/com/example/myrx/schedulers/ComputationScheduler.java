package com.example.myrx.schedulers;

import com.example.myrx.Scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ComputationScheduler implements Scheduler {
    private final ExecutorService executor = Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors()),
            new NamedThreadFactory("comp-")
    );

    @Override
    public void execute(Runnable task) {
        executor.execute(task);
    }
}