package com.beetle.room;

import org.webrtc.MediaStreamTrack;

interface LocalMedia {
    MediaStreamTrack getTrack();
    void close();
}
