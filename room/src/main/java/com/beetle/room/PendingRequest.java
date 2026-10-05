package com.beetle.room;

import protooclient.Request;

class PendingRequest {
    final Request request;
    final ResponseHandler handler;

    PendingRequest(Request request, ResponseHandler handler) {
        this.request = request;
        this.handler = handler;
    }
}
