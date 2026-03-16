package com.example.myrx;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Demo {
    public static void main(String[] args) throws Exception {
        CountDownLatch done = new CountDownLatch(1);

        Observable.<Integer>create(em -> {
                    System.out.println("create() thread = " + Thread.currentThread().getName());
                    for (int i = 1; i <= 5; i++) {
                        em.onNext(i);
                    }
                    em.onComplete();
                })
                .subscribeOn(Schedulers.io())
                .map(x -> x * 10)
                .filter(x -> x >= 30)
                .observeOn(Schedulers.single())
                .subscribe(new com.example.myrx.Observer<Integer>() {
                    @Override
                    public void onNext(Integer item) {
                        System.out.println("onNext(" + item + ") thread = " + Thread.currentThread().getName());
                    }

                    @Override
                    public void onError(Throwable t) {
                        t.printStackTrace();
                        done.countDown();
                    }

                    @Override
                    public void onComplete() {
                        System.out.println("onComplete thread = " + Thread.currentThread().getName());
                        done.countDown();
                    }
                });

        done.await(2, TimeUnit.SECONDS);
    }
}