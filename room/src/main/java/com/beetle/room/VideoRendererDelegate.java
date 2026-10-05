package com.beetle.room;

import org.webrtc.VideoSink;

public interface VideoRendererDelegate {
    VideoSink createRenderer(String id, boolean isLocal);
    void removeRenderer(String id);
}
