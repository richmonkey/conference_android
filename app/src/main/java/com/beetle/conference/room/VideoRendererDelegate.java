package com.beetle.conference.room;

import org.webrtc.SurfaceViewRenderer;

public interface VideoRendererDelegate {
    SurfaceViewRenderer createRenderer(String id, boolean isLocal);
    void removeRenderer(String id);
}
