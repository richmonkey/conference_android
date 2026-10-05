package com.beetle.conference.room;

public interface ProduceCallback {
    void onSuccess(Producer producer);
    void onError();
}
