package com.example.myrx.disposables;

import com.example.myrx.Disposable;

import java.util.concurrent.atomic.AtomicBoolean;

public final class BooleanDisposable implements Disposable {
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    @Override
    public void dispose() {
        disposed.set(true);
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }
}