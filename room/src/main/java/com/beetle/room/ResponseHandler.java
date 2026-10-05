package com.beetle.room;

import protooclient.Response;

interface ResponseHandler {
    void onSuccess(Response resp);
    void onError(Response resp);
}
