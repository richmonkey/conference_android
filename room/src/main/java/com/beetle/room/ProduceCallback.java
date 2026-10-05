package com.beetle.room;

public interface ProduceCallback {
    void onSuccess(Producer producer);
    void onError();
}
