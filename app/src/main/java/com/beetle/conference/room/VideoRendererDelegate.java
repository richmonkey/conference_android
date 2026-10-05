package com.beetle.conference.room;

import org.webrtc.VideoSink;

public interface VideoRendererDelegate {
    VideoSink createRenderer(String id, boolean isLocal);
    void removeRenderer(String id);
}
