package com.example.myrx.schedulers;

import com.example.myrx.Scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class IOThreadScheduler implements Scheduler {
    private final ExecutorService executor =
            Executors.newCachedThreadPool(new NamedThreadFactory("io-"));

    @Override
    public void execute(Runnable task) {
        executor.execute(task);
    }
}