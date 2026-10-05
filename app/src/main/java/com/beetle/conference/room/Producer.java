package com.beetle.conference.room;

import org.json.JSONObject;
import org.mediasoup.SendTransport;
import org.webrtc.AudioSource;
import org.webrtc.MediaStreamTrack;
import org.webrtc.RtpSender;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;

public class Producer {
    public String id;
    public String localId;
    private RtpSender rtpSender;
    private MediaStreamTrack track;
    public JSONObject rtpParameters;
    public String kind;
    private AudioSource audioSource;
    private VideoSource videoSource;
    private VideoCapturer videoCapturer;
    private SendTransport sendTransport;
    private boolean closed = false;

    Producer(String id, String localId, RtpSender rtpSender, MediaStreamTrack track,
             JSONObject rtpParameters, String kind, VideoSource videoSource,
             VideoCapturer videoCapturer, SendTransport transport) {
        this.id = id;
        this.localId = localId;
        this.rtpSender = rtpSender;
        this.track = track;
        this.rtpParameters = rtpParameters;
        this.kind = kind;
        this.videoSource = videoSource;
        this.videoCapturer = videoCapturer;
        this.sendTransport = transport;
    }

    Producer(String id, String localId, RtpSender rtpSender, MediaStreamTrack track,
             JSONObject rtpParameters, String kind, AudioSource audioSource, SendTransport transport) {
        this.id = id;
        this.localId = localId;
        this.rtpSender = rtpSender;
        this.track = track;
        this.rtpParameters = rtpParameters;
        this.kind = kind;
        this.audioSource = audioSource;
        this.sendTransport = transport;
    }

    public VideoCapturer getVideoCapturer() {
        return videoCapturer;
    }

    MediaStreamTrack getTrack() {
        return track;
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        sendTransport.closeProducer(localId);

        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }

        if (track != null) {
            track.dispose();
            track = null;
        }

        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }
    }
}
