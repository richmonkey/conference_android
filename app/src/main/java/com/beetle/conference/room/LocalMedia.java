package com.beetle.conference.room;

import org.webrtc.MediaStreamTrack;

interface LocalMedia {
    MediaStreamTrack getTrack();
    void close();
}
