package com.beetle.room;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import protooclient.Notification;
import protooclient.PeerListener;
import protooclient.Request;
import protooclient.Response;

class RoomPeerListener implements PeerListener {
    private final RoomClient roomClient;

    RoomPeerListener(RoomClient roomClient) {
        this.roomClient = roomClient;
    }

    @Override
    public void onClose() {
        roomClient.handler.post(roomClient::handlePeerClosed);
    }

    @Override
    public void onDisconnected() {
        roomClient.handler.post(roomClient::handlePeerDisconnected);
    }

    @Override
    public void onFailed() {
        roomClient.handler.post(roomClient::handlePeerFailed);
    }

    @Override
    public void onNotification(Notification notification) {
        String method = notification.getMethod();
        try {
            Log.i(RoomClient.TAG, "handle notification:" + method + " data:" + notification.getData());

            if (method.equals("newPeer")) {
                JSONObject object = new JSONObject(notification.getData());
                String peerId = object.getString("peerId");
                String displayName = object.getString("displayName");
                Log.i(RoomClient.TAG, "new peer id:" + peerId + " name:" + displayName);
                roomClient.handler.post(() -> roomClient.observer.onPeer(peerId));
            } else if (method.equals("peerClosed")) {
                JSONObject object = new JSONObject(notification.getData());
                String peerId = object.getString("peerId");
                roomClient.handler.post(() -> roomClient.observer.onPeerClosed(peerId));
            } else if (method.equals("newProducer")) {
                JSONObject object = new JSONObject(notification.getData());
                String id = object.getString("id");
                String kind = object.getString("kind");
                String peerId = object.getString("peerId");

                Log.i(RoomClient.TAG, "new producer id:" + id + " kind:" + kind + " peer id:" + peerId);
                roomClient.handler.post(() -> roomClient.consumeProducer(id, peerId));
            } else if (method.equals("consumerClosed")) {
                JSONObject object = new JSONObject(notification.getData());
                String consumerId = object.getString("consumerId");

                roomClient.handler.post(() -> {
                    Consumer consumer = roomClient.consumers.get(consumerId);
                    if (consumer == null) {
                        return;
                    }
                    consumer.close();

                    if (consumer.kind.equals(MediaKind.VIDEO.wireValue())) {
                        roomClient.videoRendererDelegate.removeRenderer(consumer.id);
                    }
                    roomClient.consumers.remove(consumerId);
                });
            } else if (method.equals("consumerPaused")) {
                JSONObject object = new JSONObject(notification.getData());
                String consumerId = object.getString("consumerId");
                roomClient.handler.post(() -> Log.i(RoomClient.TAG, "consumer:" + consumerId + " paused"));
            } else if (method.equals("consumerResumed")) {
                JSONObject object = new JSONObject(notification.getData());
                String consumerId = object.getString("consumerId");
                Log.i(RoomClient.TAG, "consumer:" + consumerId + " resumed");
            } else {
                Log.i(RoomClient.TAG, "unhandled notification:" + method + " data:" + notification.getData());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onOpen() {
        Log.i(RoomClient.TAG, "on open");
        roomClient.handler.post(roomClient::handlePeerOpened);
    }

    @Override
    public void onRequest(Request request) {
        Log.i(RoomClient.TAG, "on request");
    }

    @Override
    public void onResponse(Response response) {
        Log.i(RoomClient.TAG, "on response:" + response.getId() + " " + response.getData() + " " + response.getOk());
        if (!response.getOk()) {
            Log.w(RoomClient.TAG, "on response err: " + response.getErrorCode() + " " + response.getErrorReason());
        }

        roomClient.handler.post(() -> {
            PendingRequest pendingRequest = roomClient.pendingRequests.get(response.getId());
            if (pendingRequest == null) {
                Log.w(RoomClient.TAG, "Can't find request with response id:" + response.getId());
                return;
            }
            roomClient.pendingRequests.remove(response.getId());
            if (response.getOk()) {
                pendingRequest.handler.onSuccess(response);
            } else {
                pendingRequest.handler.onError(response);
            }
        });
    }
}
