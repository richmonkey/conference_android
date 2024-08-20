package com.beetle.conference;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import protooclient.Notification;
import protooclient.Peer;
import protooclient.PeerListener;
import protooclient.Request;
import protooclient.Response;


public class ProtooTest {
    private static final String TAG = "ProtooTest";



    Object lockOpen;
    boolean opened;



    class PeerListenerImpl implements PeerListener {

        public void onClose() {

        }
        public void onDisconnected() {

        }
        public void onFailed() {

        }
        public void onNotification(Notification p0) {

        }
        public void onOpen() {
            Log.i(TAG, "on open");
            synchronized (lockOpen) {
                opened = true;
                lockOpen.notify();
            }
        }

        public void onRequest(Request p0) {
            Log.i(TAG, "on request");
        }
        public void onResponse(Response p0) {
            Log.i(TAG, "on response:" + p0.getId() + " " + p0.getData() + " " + p0.getOk());
        }
    }

    @Before
    public void createHandler() {
        opened = false;
        lockOpen = new Object();
    }

    @Test
    public void Protoo_PeerOpen() {
        Peer peer = new Peer("ws://192.168.1.101:4444/?peerId=1&roomId=1&mode=group", new PeerListenerImpl());
        peer.open();
        try {
            synchronized (lockOpen) {
                while (!opened) {
                    lockOpen.wait();
                }
            }

            try {
                JSONObject j = new JSONObject();
                j.put("token", "abc");
                Request req = new Request(1, "auth", j.toString());
                peer.request(req);
            } catch (Exception e) {
                e.printStackTrace();
            }
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
