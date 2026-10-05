package com.beetle.room;

import org.json.JSONObject;
import org.mediasoup.RecvTransport;
import org.webrtc.MediaStreamTrack;
import org.webrtc.RtpReceiver;

public class Consumer {
    public String id;
    public String localId;
    public String producerId;
    private RtpReceiver rtpReceiver;
    private MediaStreamTrack track;
    public JSONObject rtpParameters;
    public String peerId;
    public String kind;
    private RecvTransport recvTransport;
    private boolean closed = false;

    public MediaStreamTrack getTrack() {
        return track;
    }

    RtpReceiver getRtpReceiver() {
        return rtpReceiver;
    }

    Consumer(String id, String localId, String producerId, RtpReceiver rtpReceiver,
             MediaStreamTrack track, JSONObject rtpParameters, String kind, String peerId,
             RecvTransport transport) {
        this.id = id;
        this.localId = localId;
        this.producerId = producerId;
        this.rtpReceiver = rtpReceiver;
        this.track = track;
        this.rtpParameters = rtpParameters;
        this.kind = kind;
        this.peerId = peerId;
        this.recvTransport = transport;
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        recvTransport.closeConsumer(localId);
    }
}
