package com.beetle.room;

import org.webrtc.MediaStreamTrack;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

final class LocalVideoMedia implements LocalMedia {
    private final VideoTrack track;
    private VideoSource source;
    private VideoCapturer capturer;

    LocalVideoMedia(VideoTrack track, VideoSource source, VideoCapturer capturer) {
        this.track = track;
        this.source = source;
        this.capturer = capturer;
    }

    @Override
    public MediaStreamTrack getTrack() {
        return track;
    }

    VideoCapturer getCapturer() {
        return capturer;
    }

    @Override
    public void close() {
        if (capturer != null) {
            try {
                capturer.stopCapture();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            capturer.dispose();
            capturer = null;
        }
        track.dispose();
        if (source != null) {
            source.dispose();
            source = null;
        }
    }
}
