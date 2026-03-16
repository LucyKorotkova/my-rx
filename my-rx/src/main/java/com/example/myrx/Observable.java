package com.example.myrx;

import com.example.myrx.disposables.BooleanDisposable;
import com.example.myrx.disposables.CompositeDisposable;
import com.example.myrx.disposables.SequentialDisposable;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Observable<T> {

    @FunctionalInterface
    private interface OnSubscribe<T> {
        Disposable subscribe(Observer<? super T> observer);
    }

    private final OnSubscribe<T> onSubscribe;

    private Observable(OnSubscribe<T> onSubscribe) {
        this.onSubscribe = onSubscribe;
    }

    public static <T> Observable<T> create(ObservableOnSubscribe<T> source) {
        Objects.requireNonNull(source, "source");
        return new Observable<>(downstream -> {
            SafeObserver<T> safe = new SafeObserver<>(downstream);
            CreateEmitter<T> emitter = new CreateEmitter<>(safe);
            try {
                source.subscribe(emitter);
            } catch (Throwable ex) {
                emitter.onError(ex);
            }
            return emitter;
        });
    }

    public Disposable subscribe(Observer<? super T> observer) {
        Objects.requireNonNull(observer, "observer");
        return onSubscribe.subscribe(observer);
    }


    public <R> Observable<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return new Observable<>(downstream -> {
            SequentialDisposable sd = new SequentialDisposable();

            Disposable up = Observable.this.subscribe(new Observer<T>() {
                @Override
                public void onNext(T item) {
                    if (sd.isDisposed()) return;
                    final R mapped;
                    try {
                        mapped = mapper.apply(item);
                    } catch (Throwable ex) {
                        onError(ex);
                        return;
                    }
                    downstream.onNext(mapped);
                }

                @Override
                public void onError(Throwable t) {
                    if (sd.isDisposed()) return;
                    sd.dispose();
                    downstream.onError(t);
                }

                @Override
                public void onComplete() {
                    if (sd.isDisposed()) return;
                    sd.dispose();
                    downstream.onComplete();
                }
            });

            sd.replace(up);
            return sd;
        });
    }

    public Observable<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return new Observable<>(downstream -> {
            SequentialDisposable sd = new SequentialDisposable();

            Disposable up = Observable.this.subscribe(new Observer<T>() {
                @Override
                public void onNext(T item) {
                    if (sd.isDisposed()) return;
                    final boolean pass;
                    try {
                        pass = predicate.test(item);
                    } catch (Throwable ex) {
                        onError(ex);
                        return;
                    }
                    if (pass) downstream.onNext(item);
                }

                @Override
                public void onError(Throwable t) {
                    if (sd.isDisposed()) return;
                    sd.dispose();
                    downstream.onError(t);
                }

                @Override
                public void onComplete() {
                    if (sd.isDisposed()) return;
                    sd.dispose();
                    downstream.onComplete();
                }
            });

            sd.replace(up);
            return sd;
        });
    }

    public <R> Observable<R> flatMap(Function<? super T, ? extends Observable<? extends R>> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return new Observable<>(actualDownstream -> {

            SerializedObserver<R> downstream = new SerializedObserver<>(actualDownstream);

            CompositeDisposable cd = new CompositeDisposable();
            SequentialDisposable upstreamSd = new SequentialDisposable();
            cd.add(upstreamSd);

            AtomicInteger wip = new AtomicInteger(1); // 1 = upstream активен
            AtomicBoolean once = new AtomicBoolean(false);

            Disposable up = Observable.this.subscribe(new Observer<T>() {
                @Override
                public void onNext(T item) {
                    if (cd.isDisposed()) return;

                    final Observable<? extends R> inner;
                    try {
                        inner = mapper.apply(item);
                    } catch (Throwable ex) {
                        onError(ex);
                        return;
                    }
                    if (inner == null) {
                        onError(new NullPointerException("flatMap mapper returned null"));
                        return;
                    }

                    wip.incrementAndGet();

                    Disposable innerD = inner.subscribe(new Observer<R>() {
                        @Override
                        public void onNext(R r) {
                            if (cd.isDisposed()) return;
                            downstream.onNext(r);
                        }

                        @Override
                        public void onError(Throwable t) {
                            fail(t);
                        }

                        @Override
                        public void onComplete() {
                            doneOne();
                        }
                    });

                    cd.add(innerD);
                }

                @Override
                public void onError(Throwable t) {
                    fail(t);
                }

                @Override
                public void onComplete() {
                    doneOne();
                }

                private void doneOne() {
                    if (wip.decrementAndGet() == 0 && once.compareAndSet(false, true)) {
                        cd.dispose();
                        downstream.onComplete();
                    }
                }

                private void fail(Throwable t) {
                    if (once.compareAndSet(false, true)) {
                        cd.dispose();
                        downstream.onError(t);
                    }
                }
            });

            upstreamSd.replace(up);
            return cd;
        });
    }

    public Observable<T> subscribeOn(Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler");
        return new Observable<>(downstream -> {
            SequentialDisposable sd = new SequentialDisposable();
            scheduler.execute(() -> {
                if (sd.isDisposed()) return;
                Disposable up = Observable.this.subscribe(downstream);
                sd.replace(up);
            });
            return sd;
        });
    }

    public Observable<T> observeOn(Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler");
        return new Observable<>(downstream -> {
            ObserveOnObserver<T> parent = new ObserveOnObserver<>(downstream, scheduler);
            Disposable up = Observable.this.subscribe(parent);
            parent.setUpstream(up);
            return parent;
        });
    }


    private static final class SafeObserver<T> implements Observer<T> {
        private final Observer<? super T> downstream;
        SafeObserver(Observer<? super T> downstream) {
            this.downstream = downstream;
        }

        @Override public void onNext(T item) { downstream.onNext(item); }
        @Override public void onError(Throwable t) { downstream.onError(t); }
        @Override public void onComplete() { downstream.onComplete(); }
    }

    private static final class CreateEmitter<T> implements Emitter<T>, Disposable {
        private final Observer<? super T> downstream;
        private final AtomicBoolean disposed = new AtomicBoolean(false);
        private final AtomicBoolean terminated = new AtomicBoolean(false);

        CreateEmitter(Observer<? super T> downstream) {
            this.downstream = downstream;
        }

        @Override
        public void onNext(T item) {
            if (isDisposed() || terminated.get()) return;
            if (item == null) {
                onError(new NullPointerException("onNext item == null"));
                return;
            }
            try {
                downstream.onNext(item);
            } catch (Throwable ex) {
                onError(ex);
            }
        }

        @Override
        public void onError(Throwable t) {
            if (t == null) t = new NullPointerException("onError throwable == null");
            if (isDisposed() || !terminated.compareAndSet(false, true)) return;
            try {
                downstream.onError(t);
            } finally {
                dispose();
            }
        }

        @Override
        public void onComplete() {
            if (isDisposed() || !terminated.compareAndSet(false, true)) return;
            try {
                downstream.onComplete();
            } finally {
                dispose();
            }
        }

        @Override public boolean isDisposed() { return disposed.get(); }
        @Override public void dispose() { disposed.set(true); }
    }

    private static final class SerializedObserver<T> implements Observer<T> {
        private final Observer<? super T> downstream;
        private boolean done;

        SerializedObserver(Observer<? super T> downstream) {
            this.downstream = downstream;
        }

        @Override
        public synchronized void onNext(T item) {
            if (done) return;
            downstream.onNext(item);
        }

        @Override
        public synchronized void onError(Throwable t) {
            if (done) return;
            done = true;
            downstream.onError(t);
        }

        @Override
        public synchronized void onComplete() {
            if (done) return;
            done = true;
            downstream.onComplete();
        }
    }

    private static final class ObserveOnObserver<T> implements Observer<T>, Disposable {
        private final Observer<? super T> downstream;
        private final Scheduler scheduler;

        private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
        private final AtomicInteger wip = new AtomicInteger(0);

        private final AtomicBoolean disposed = new AtomicBoolean(false);
        private final AtomicBoolean done = new AtomicBoolean(false);

        private final SequentialDisposable upstream = new SequentialDisposable();

        ObserveOnObserver(Observer<? super T> downstream, Scheduler scheduler) {
            this.downstream = downstream;
            this.scheduler = scheduler;
        }

        void setUpstream(Disposable up) {
            upstream.replace(up);
        }

        @Override
        public void onNext(T item) {
            if (isDisposed() || done.get()) return;
            queue.offer(() -> downstream.onNext(item));
            scheduleDrain();
        }

        @Override
        public void onError(Throwable t) {
            if (isDisposed() || !done.compareAndSet(false, true)) return;
            queue.offer(() -> downstream.onError(t));
            scheduleDrain();
        }

        @Override
        public void onComplete() {
            if (isDisposed() || !done.compareAndSet(false, true)) return;
            queue.offer(downstream::onComplete);
            scheduleDrain();
        }

        private void scheduleDrain() {
            if (wip.getAndIncrement() == 0) {
                scheduler.execute(this::drain);
            }
        }

        private void drain() {
            int missed = 1;
            for (;;) {
                if (isDisposed()) {
                    queue.clear();
                    return;
                }

                Runnable r;
                while ((r = queue.poll()) != null) {
                    if (isDisposed()) {
                        queue.clear();
                        return;
                    }
                    try {
                        r.run();
                    } catch (Throwable ex) {
                        dispose();
                        downstream.onError(ex);
                        return;
                    }
                }

                missed = wip.addAndGet(-missed);
                if (missed == 0) return;
            }
        }

        @Override
        public void dispose() {
            if (!disposed.compareAndSet(false, true)) return;
            upstream.dispose();
            queue.clear();
        }

        @Override
        public boolean isDisposed() {
            return disposed.get();
        }
    }
}