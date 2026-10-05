package com.beetle.room;

import android.content.Context;
import android.graphics.PixelFormat;
import android.util.Log;

import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;

final class WebRtcRuntime {
    private static final String TAG = "WebRtcRuntime";

    private EglBase rootEglBase;
    private SurfaceTextureHelper surfaceTextureHelper;
    private PeerConnectionFactory peerConnectionFactory;
    private final PeerConnection.RTCConfiguration rtcConfiguration;

    WebRtcRuntime(Context appContext) {
        rootEglBase = EglBase.create();
        surfaceTextureHelper = SurfaceTextureHelper.create(
                "CaptureThread", rootEglBase.getEglBaseContext());
        peerConnectionFactory = createPeerConnectionFactory(
                new PeerConnectionFactory.Options(), rootEglBase, appContext);
        rtcConfiguration = createRtcConfiguration();
    }

    EglBase getRootEglBase() {
        return rootEglBase;
    }

    SurfaceTextureHelper getSurfaceTextureHelper() {
        return surfaceTextureHelper;
    }

    PeerConnectionFactory getPeerConnectionFactory() {
        return peerConnectionFactory;
    }

    PeerConnection.RTCConfiguration getRtcConfiguration() {
        return rtcConfiguration;
    }

    SurfaceViewRenderer createRenderer(Context context, boolean isLocal) {
        SurfaceViewRenderer renderer = new SurfaceViewRenderer(context);
        renderer.init(rootEglBase.getEglBaseContext(), null);
        if (isLocal) {
            renderer.setZOrderMediaOverlay(true);
            renderer.setMirror(true);
            renderer.getHolder().setFormat(PixelFormat.TRANSPARENT);
        }
        return renderer;
    }

    void release() {
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
        if (rootEglBase != null) {
            rootEglBase.release();
            rootEglBase = null;
        }
    }

    private PeerConnection.RTCConfiguration createRtcConfiguration() {
        PeerConnection.RTCConfiguration configuration =
                new PeerConnection.RTCConfiguration(new ArrayList<>());
        configuration.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        configuration.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        configuration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        configuration.continualGatheringPolicy =
                PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        configuration.keyType = PeerConnection.KeyType.ECDSA;
        configuration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        return configuration;
    }

    private PeerConnectionFactory createPeerConnectionFactory(
            PeerConnectionFactory.Options options, EglBase eglBase, Context appContext) {
        AudioDeviceModule audioDeviceModule = createJavaAudioDevice(appContext);
        final boolean enableH264HighProfile = true;
        final boolean enableIntelVp8Encoder = true;
        VideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                eglBase.getEglBaseContext(), enableIntelVp8Encoder, enableH264HighProfile);
        VideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());

        PeerConnectionFactory factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
        audioDeviceModule.release();
        return factory;
    }

    private AudioDeviceModule createJavaAudioDevice(Context appContext) {
        JavaAudioDeviceModule.AudioTrackErrorCallback callback =
                new JavaAudioDeviceModule.AudioTrackErrorCallback() {
                    @Override
                    public void onWebRtcAudioTrackInitError(String errorMessage) {
                        Log.e(TAG, "onWebRtcAudioTrackInitError: " + errorMessage);
                    }

                    @Override
                    public void onWebRtcAudioTrackStartError(
                            JavaAudioDeviceModule.AudioTrackStartErrorCode errorCode,
                            String errorMessage) {
                        Log.e(TAG, "onWebRtcAudioTrackStartError: " + errorCode + ". " + errorMessage);
                    }

                    @Override
                    public void onWebRtcAudioTrackError(String errorMessage) {
                        Log.e(TAG, "onWebRtcAudioTrackError: " + errorMessage);
                    }
                };
        return JavaAudioDeviceModule.builder(appContext)
                .setAudioTrackErrorCallback(callback)
                .createAudioDeviceModule();
    }
}
