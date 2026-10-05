package com.beetle.room;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.MediaStreamTrack;

final class LocalAudioMedia implements LocalMedia {
    private final AudioTrack track;
    private AudioSource source;

    LocalAudioMedia(AudioTrack track, AudioSource source) {
        this.track = track;
        this.source = source;
    }

    @Override
    public MediaStreamTrack getTrack() {
        return track;
    }

    @Override
    public void close() {
        track.dispose();
        if (source != null) {
            source.dispose();
            source = null;
        }
    }
}
