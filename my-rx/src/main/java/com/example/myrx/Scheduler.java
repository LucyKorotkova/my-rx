package com.example.myrx;

public interface Scheduler {
    void execute(Runnable task);
}