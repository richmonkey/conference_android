package com.beetle.room;

import android.content.Context;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStreamTrack;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

final class LocalMediaController {
    private static final String TAG = "LocalMediaController";
    private final WebRtcRuntime runtime;
    private final RoomClientConfig.VideoCaptureSpec videoCapture;

    LocalMediaController(WebRtcRuntime runtime, RoomClientConfig.VideoCaptureSpec videoCapture) {
        this.runtime = runtime;
        this.videoCapture = videoCapture;
    }

    LocalVideoMedia createVideo(Context context, VideoSink sink) {
        VideoSource source = runtime.getPeerConnectionFactory().createVideoSource(false);
        VideoTrack track = runtime.getPeerConnectionFactory()
                .createVideoTrack(RoomClient.VIDEO_TRACK_ID, source);
        track.setEnabled(true);
        if (sink != null) {
            track.addSink(sink);
        }

        VideoCapturer capturer = createVideoCapturer(source, context);
        if (capturer == null) {
            track.dispose();
            source.dispose();
            return null;
        }
        try {
            capturer.startCapture(videoCapture.getWidth(), videoCapture.getHeight(), videoCapture.getFps());
        } catch (RuntimeException e) {
            capturer.dispose();
            track.dispose();
            source.dispose();
            throw e;
        }
        return new LocalVideoMedia(track, source, capturer);
    }

    LocalAudioMedia createAudio(boolean muted) {
        AudioSource source = runtime.getPeerConnectionFactory()
                .createAudioSource(new MediaConstraints());
        AudioTrack track = runtime.getPeerConnectionFactory()
                .createAudioTrack(RoomClient.AUDIO_TRACK_ID, source);
        track.setEnabled(!muted);
        return new LocalAudioMedia(track, source);
    }

    boolean switchCamera(LocalMedia media) {
        if (!(media instanceof LocalVideoMedia)) {
            return false;
        }
        VideoCapturer capturer = ((LocalVideoMedia) media).getCapturer();
        if (!(capturer instanceof CameraVideoCapturer)) {
            Log.d(TAG, "Will not switch camera, video capturer is not a camera");
            return false;
        }
        ((CameraVideoCapturer) capturer).switchCamera(null);
        return true;
    }

    void setMuted(LocalMedia media, boolean muted) {
        if (media == null) {
            return;
        }
        MediaStreamTrack track = media.getTrack();
        if (track instanceof AudioTrack) {
            track.setEnabled(!muted);
        }
    }

    private VideoCapturer createVideoCapturer(VideoSource source, Context context) {
        CameraEnumerator enumerator = Camera2Enumerator.isSupported(context)
                ? new Camera2Enumerator(context)
                : new Camera1Enumerator(true);
        VideoCapturer capturer = createCameraCapturer(enumerator);
        if (capturer != null) {
            capturer.initialize(runtime.getSurfaceTextureHelper(), context,
                    source.getCapturerObserver());
        }
        return capturer;
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        for (String deviceName : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) {
                    return capturer;
                }
            }
        }
        for (String deviceName : enumerator.getDeviceNames()) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) {
                    return capturer;
                }
            }
        }
        return null;
    }
}
