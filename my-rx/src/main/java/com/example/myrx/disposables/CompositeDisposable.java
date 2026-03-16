package com.example.myrx.disposables;

import com.example.myrx.Disposable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CompositeDisposable implements Disposable {
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final Set<Disposable> set = ConcurrentHashMap.newKeySet();

    public boolean add(Disposable d) {
        if (disposed.get()) {
            d.dispose();
            return false;
        }
        set.add(d);
        if (disposed.get()) {

            if (set.remove(d)) d.dispose();
            return false;
        }
        return true;
    }

    @Override
    public void dispose() {
        if (!disposed.compareAndSet(false, true)) return;
        for (Disposable d : set) {
            d.dispose();
        }
        set.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }
}