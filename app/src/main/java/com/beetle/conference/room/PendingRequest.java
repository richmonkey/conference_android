package com.beetle.conference.room;

import protooclient.Request;

class PendingRequest {
    final Request request;
    final ResponseHandler handler;

    PendingRequest(Request request, ResponseHandler handler) {
        this.request = request;
        this.handler = handler;
    }
}
