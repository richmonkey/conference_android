package com.beetle.room;

import org.json.JSONObject;
import org.mediasoup.SendTransport;
import org.webrtc.MediaStreamTrack;
import org.webrtc.RtpSender;
import org.webrtc.VideoCapturer;

public class Producer {
    public String id;
    public String localId;
    private RtpSender rtpSender;
    private LocalMedia localMedia;
    public JSONObject rtpParameters;
    public String kind;
    private SendTransport sendTransport;
    private boolean closed = false;

    Producer(String id, String localId, RtpSender rtpSender, LocalMedia localMedia,
             JSONObject rtpParameters, String kind, SendTransport transport) {
        this.id = id;
        this.localId = localId;
        this.rtpSender = rtpSender;
        this.localMedia = localMedia;
        this.rtpParameters = rtpParameters;
        this.kind = kind;
        this.sendTransport = transport;
    }

    public VideoCapturer getVideoCapturer() {
        if (localMedia instanceof LocalVideoMedia) {
            return ((LocalVideoMedia) localMedia).getCapturer();
        }
        return null;
    }

    MediaStreamTrack getTrack() {
        return localMedia == null ? null : localMedia.getTrack();
    }

    LocalMedia getLocalMedia() {
        return localMedia;
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        sendTransport.closeProducer(localId);

        if (localMedia != null) {
            localMedia.close();
            localMedia = null;
        }
    }
}
