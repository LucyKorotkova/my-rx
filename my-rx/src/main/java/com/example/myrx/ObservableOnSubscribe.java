package com.example.myrx;

@FunctionalInterface
public interface ObservableOnSubscribe<T> {
    void subscribe(Emitter<T> emitter) throws Exception;
}