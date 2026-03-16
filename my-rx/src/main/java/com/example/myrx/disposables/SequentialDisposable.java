package com.example.myrx.disposables;

import com.example.myrx.Disposable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class SequentialDisposable implements Disposable {
    private static final Disposable DISPOSED = new Disposable() {
        @Override public void dispose() {}
        @Override public boolean isDisposed() { return true; }
    };

    private final AtomicReference<Disposable> ref = new AtomicReference<>();

    public void replace(Disposable next) {
        Objects.requireNonNull(next, "next");
        for (;;) {
            Disposable current = ref.get();
            if (current == DISPOSED) {
                next.dispose();
                return;
            }
            if (ref.compareAndSet(current, next)) {
                if (current != null) current.dispose();
                return;
            }
        }
    }

    @Override
    public void dispose() {
        Disposable current = ref.getAndSet(DISPOSED);
        if (current != null && current != DISPOSED) current.dispose();
    }

    @Override
    public boolean isDisposed() {
        return ref.get() == DISPOSED;
    }
}