package com.example.myrx.schedulers;

import com.example.myrx.Scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SingleThreadScheduler implements Scheduler {
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(new NamedThreadFactory("single-"));

    @Override
    public void execute(Runnable task) {
        executor.execute(task);
    }
}