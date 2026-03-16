package com.example.myrx;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ObservableTest {

    @Test
    void create_emitsAndCompletes() throws Exception {
        Vector<Integer> values = new Vector<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Observable.<Integer>create(em -> {
            em.onNext(1);
            em.onNext(2);
            em.onNext(3);
            em.onComplete();
        }).subscribe(new Observer<>() {
            @Override public void onNext(Integer item) { values.add(item); }
            @Override public void onError(Throwable t) { error.set(t); done.countDown(); }
            @Override public void onComplete() { done.countDown(); }
        });

        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertNull(error.get());
        assertEquals(List.of(1, 2, 3), values);
    }

    @Test
    void map_works() throws Exception {
        Vector<Integer> values = new Vector<>();
        CountDownLatch done = new CountDownLatch(1);

        Observable.<Integer>create(em -> {
                    em.onNext(1);
                    em.onNext(2);
                    em.onNext(3);
                    em.onComplete();
                }).map(x -> x * 10)
                .subscribe(new Observer<>() {
                    @Override public void onNext(Integer item) { values.add(item); }
                    @Override public void onError(Throwable t) { fail(t); }
                    @Override public void onComplete() { done.countDown(); }
                });

        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertEquals(List.of(10, 20, 30), values);
    }

    @Test
    void filter_works() throws Exception {
        Vector<Integer> values = new Vector<>();
        CountDownLatch done = new CountDownLatch(1);

        Observable.<Integer>create(em -> {
                    for (int i = 1; i <= 6; i++) em.onNext(i);
                    em.onComplete();
                }).filter(x -> x % 2 == 0)
                .subscribe(new Observer<>() {
                    @Override public void onNext(Integer item) { values.add(item); }
                    @Override public void onError(Throwable t) { fail(t); }
                    @Override public void onComplete() { done.countDown(); }
                });

        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertEquals(List.of(2, 4, 6), values);
    }

    @Test
    void flatMap_mergesInnerObservables() throws Exception {
        Vector<Integer> values = new Vector<>();
        CountDownLatch done = new CountDownLatch(1);

        Observable.<Integer>create(em -> {
            em.onNext(1);
            em.onNext(2);
            em.onNext(3);
            em.onComplete();
        }).flatMap(x -> Observable.<Integer>create(inner -> {
            inner.onNext(x);
            inner.onNext(x * 100);
            inner.onComplete();
        })).subscribe(new Observer<>() {
            @Override public void onNext(Integer item) { values.add(item); }
            @Override public void onError(Throwable t) { fail(t); }
            @Override public void onComplete() { done.countDown(); }
        });

        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertEquals(6, values.size());
        assertTrue(values.containsAll(List.of(1, 100, 2, 200, 3, 300)));
    }

    @Test
    void error_isDeliveredToOnError() throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Observable.<Integer>create(em -> {
            em.onNext(1);
            em.onError(new IllegalStateException("boom"));
        }).subscribe(new Observer<>() {
            @Override public void onNext(Integer item) {}
            @Override public void onError(Throwable t) { error.set(t); done.countDown(); }
            @Override public void onComplete() { done.countDown(); }
        });

        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertNotNull(error.get());
        assertEquals("boom", error.get().getMessage());
    }

    @Test
    void subscribeOn_runsSubscriptionOnSchedulerThread() throws Exception {
        AtomicReference<String> sourceThread = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Observable.<Integer>create(em -> {
                    sourceThread.set(Thread.currentThread().getName());
                    em.onNext(1);
                    em.onComplete();
                }).subscribeOn(Schedulers.single())
                .subscribe(new Observer<>() {
                    @Override public void onNext(Integer item) {}
                    @Override public void onError(Throwable t) { fail(t); }
                    @Override public void onComplete() { done.countDown(); }
                });

        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertNotNull(sourceThread.get());
        assertTrue(sourceThread.get().startsWith("single-"), "thread=" + sourceThread.get());
    }

    @Test
    void observeOn_movesCallbacksToSchedulerThread() throws Exception {
        AtomicReference<String> onNextThread = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Observable.<Integer>create(em -> {
                    em.onNext(1);
                    em.onComplete();
                }).observeOn(Schedulers.single())
                .subscribe(new Observer<>() {
                    @Override public void onNext(Integer item) {
                        onNextThread.set(Thread.currentThread().getName());
                    }
                    @Override public void onError(Throwable t) { fail(t); }
                    @Override public void onComplete() { done.countDown(); }
                });

        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertNotNull(onNextThread.get());
        assertTrue(onNextThread.get().startsWith("single-"), "thread=" + onNextThread.get());
    }

        @Test
    void disposable_stopsReceivingEvents() throws Exception {
        AtomicInteger received = new AtomicInteger(0);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch first = new CountDownLatch(1);

        AtomicReference<Disposable> ref = new AtomicReference<>();

        Observable<Integer> source = Observable.create(em -> {
            Thread t = new Thread(() -> {
                try {

                    start.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                for (int i = 0; i < 1_000; i++) {
                    if (em.isDisposed()) return;
                    em.onNext(i);
                }
                em.onComplete();
            }, "emitter-thread");

            t.setDaemon(true);
            t.start();
        });

        Disposable d = source.subscribe(new Observer<>() {
            @Override
            public void onNext(Integer item) {
                if (received.incrementAndGet() == 1) {

                    Disposable dd = ref.get();
                    if (dd != null) {
                        dd.dispose();
                    }
                    first.countDown();
                }
            }

            @Override public void onError(Throwable t) { fail(t); }
            @Override public void onComplete() {}
        });

        ref.set(d);
        start.countDown(); // запускаем эмиссию
        assertTrue(first.await(1, TimeUnit.SECONDS), "Не получили первый элемент вовремя");


        Thread.sleep(50);

        assertTrue(d.isDisposed());
        assertEquals(1, received.get(), "received=" + received.get());
    }
}