package com.beetle.conference;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;



import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mediasoup.Device;
import org.mediasoup.MediaSoupClient;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import protooclient.Notification;
import protooclient.Peer;
import protooclient.PeerListener;
import protooclient.Request;
import protooclient.Response;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;



public class MediasoupTest {
    private static final String TAG = "MediasoupTest";
    Context context;

    @Before
    public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Test
    public void testConferenceActivity() {
        Class cls = ConferenceActivity.class;


        Intent intent = new Intent(context, cls);
        //intent.setComponent(ComponentName.createRelative("com.beetle.conference", "ConferenceActivity"));
        intent.putExtra("current_uid", 1);
        intent.putExtra("channel_id", "" + 1);
        intent.putExtra("token", "111");
        ActivityScenario<ConferenceActivity> activityScenario = ActivityScenario.launch(intent);

        activityScenario.onActivity(new ActivityScenario.ActivityAction<ConferenceActivity>() {
            @Override
            public void perform(ConferenceActivity activity) {
                //activity.move
                //activityScenario.moveToState(Lifecycle.State.CREATED);
            }
        });

    }

    @Test
    public void testWebrtc() {
        Log.d(TAG, "Initialize WebRTC");
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions());

        Log.i(TAG, "mediasoup version:" + MediaSoupClient.version());

        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(new ArrayList<>());
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        //peerConnection = factory.createPeerConnection(rtcConfig, pcObserver);
        EglBase rootEglBase = EglBase.create();

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        PeerConnectionFactory pcFactory = createPeerConnectionFactory(options, rootEglBase);


        try {
            String routerRtpCaps = "{\"codecs\":[{\"channels\":2,\"clockRate\":48000,\"kind\":\"audio\",\"mimeType\":\"audio/opus\",\"parameters\":{},\"preferredPayloadType\":100,\"rtcpFeedback\":[{\"parameter\":\"\",\"type\":\"transport-cc\"}]},{\"clockRate\":90000,\"kind\":\"video\",\"mimeType\":\"video/VP8\",\"parameters\":{\"x-google-start-bitrate\":1000},\"preferredPayloadType\":101,\"rtcpFeedback\":[{\"parameter\":\"\",\"type\":\"nack\"},{\"parameter\":\"pli\",\"type\":\"nack\"},{\"parameter\":\"fir\",\"type\":\"ccm\"},{\"parameter\":\"\",\"type\":\"goog-remb\"},{\"parameter\":\"\",\"type\":\"transport-cc\"}]},{\"clockRate\":90000,\"kind\":\"video\",\"mimeType\":\"video/rtx\",\"parameters\":{\"apt\":101},\"preferredPayloadType\":102,\"rtcpFeedback\":[]},{\"clockRate\":90000,\"kind\":\"video\",\"mimeType\":\"video/VP9\",\"parameters\":{\"profile-id\":2,\"x-google-start-bitrate\":1000},\"preferredPayloadType\":103,\"rtcpFeedback\":[{\"parameter\":\"\",\"type\":\"nack\"},{\"parameter\":\"pli\",\"type\":\"nack\"},{\"parameter\":\"fir\",\"type\":\"ccm\"},{\"parameter\":\"\",\"type\":\"goog-remb\"},{\"parameter\":\"\",\"type\":\"transport-cc\"}]},{\"clockRate\":90000,\"kind\":\"video\",\"mimeType\":\"video/rtx\",\"parameters\":{\"apt\":103},\"preferredPayloadType\":104,\"rtcpFeedback\":[]},{\"clockRate\":90000,\"kind\":\"video\",\"mimeType\":\"video/H264\",\"parameters\":{\"level-asymmetry-allowed\":1,\"packetization-mode\":1,\"profile-level-id\":\"4d0032\",\"x-google-start-bitrate\":1000},\"preferredPayloadType\":105,\"rtcpFeedback\":[{\"parameter\":\"\",\"type\":\"nack\"},{\"parameter\":\"pli\",\"type\":\"nack\"},{\"parameter\":\"fir\",\"type\":\"ccm\"},{\"parameter\":\"\",\"type\":\"goog-remb\"},{\"parameter\":\"\",\"type\":\"transport-cc\"}]},{\"clockRate\":90000,\"kind\":\"video\",\"mimeType\":\"video/rtx\",\"parameters\":{\"apt\":105},\"preferredPayloadType\":106,\"rtcpFeedback\":[]},{\"clockRate\":90000,\"kind\":\"video\",\"mimeType\":\"video/H264\",\"parameters\":{\"level-asymmetry-allowed\":1,\"packetization-mode\":1,\"profile-level-id\":\"42e01f\",\"x-google-start-bitrate\":1000},\"preferredPayloadType\":107,\"rtcpFeedback\":[{\"parameter\":\"\",\"type\":\"nack\"},{\"parameter\":\"pli\",\"type\":\"nack\"},{\"parameter\":\"fir\",\"type\":\"ccm\"},{\"parameter\":\"\",\"type\":\"goog-remb\"},{\"parameter\":\"\",\"type\":\"transport-cc\"}]},{\"clockRate\":90000,\"kind\":\"video\",\"mimeType\":\"video/rtx\",\"parameters\":{\"apt\":107},\"preferredPayloadType\":108,\"rtcpFeedback\":[]}],\"headerExtensions\":[{\"direction\":\"sendrecv\",\"kind\":\"audio\",\"preferredEncrypt\":false,\"preferredId\":1,\"uri\":\"urn:ietf:params:rtp-hdrext:sdes:mid\"},{\"direction\":\"sendrecv\",\"kind\":\"video\",\"preferredEncrypt\":false,\"preferredId\":1,\"uri\":\"urn:ietf:params:rtp-hdrext:sdes:mid\"},{\"direction\":\"recvonly\",\"kind\":\"video\",\"preferredEncrypt\":false,\"preferredId\":2,\"uri\":\"urn:ietf:params:rtp-hdrext:sdes:rtp-stream-id\"},{\"direction\":\"recvonly\",\"kind\":\"video\",\"preferredEncrypt\":false,\"preferredId\":3,\"uri\":\"urn:ietf:params:rtp-hdrext:sdes:repaired-rtp-stream-id\"},{\"direction\":\"sendrecv\",\"kind\":\"audio\",\"preferredEncrypt\":false,\"preferredId\":4,\"uri\":\"http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\"},{\"direction\":\"sendrecv\",\"kind\":\"video\",\"preferredEncrypt\":false,\"preferredId\":4,\"uri\":\"http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\"},{\"direction\":\"recvonly\",\"kind\":\"audio\",\"preferredEncrypt\":false,\"preferredId\":5,\"uri\":\"http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01\"},{\"direction\":\"sendrecv\",\"kind\":\"video\",\"preferredEncrypt\":false,\"preferredId\":5,\"uri\":\"http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01\"},{\"direction\":\"sendrecv\",\"kind\":\"video\",\"preferredEncrypt\":false,\"preferredId\":6,\"uri\":\"http://tools.ietf.org/html/draft-ietf-avtext-framemarking-07\"},{\"direction\":\"sendrecv\",\"kind\":\"video\",\"preferredEncrypt\":false,\"preferredId\":7,\"uri\":\"urn:ietf:params:rtp-hdrext:framemarking\"},{\"direction\":\"sendrecv\",\"kind\":\"audio\",\"preferredEncrypt\":false,\"preferredId\":10,\"uri\":\"urn:ietf:params:rtp-hdrext:ssrc-audio-level\"},{\"direction\":\"sendrecv\",\"kind\":\"video\",\"preferredEncrypt\":false,\"preferredId\":11,\"uri\":\"urn:3gpp:video-orientation\"},{\"direction\":\"sendrecv\",\"kind\":\"video\",\"preferredEncrypt\":false,\"preferredId\":12,\"uri\":\"urn:ietf:params:rtp-hdrext:toffset\"}]}";
            JSONObject object = new JSONObject(routerRtpCaps);
            Log.i(TAG, "router rtp cap:\n" + object.toString(2));

            Device device = new Device();
            device.load(routerRtpCaps, rtcConfig, pcFactory);
            Log.i(TAG, "device load success");
            String rtpCaps = device.getRtpCapabilities();
            String sctpCaps = device.getSctpCapabilities();
            new JSONObject(rtpCaps);
            new JSONObject(sctpCaps);
            Log.i(TAG, "rtp caps:" + rtpCaps);
            Log.i(TAG, "sctp caps:" + sctpCaps);
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }


    public PeerConnectionFactory createPeerConnectionFactory(PeerConnectionFactory.Options options, EglBase rootEglBase) {
        AudioDeviceModule adm = createJavaAudioDevice();
        // Create peer connection factory.
        if (options != null) {
            Log.d(TAG, "Factory networkIgnoreMask option: " + options.networkIgnoreMask);
        }
        final boolean enableH264HighProfile = true;
        final boolean enableIntelVp8Encoder = true;
        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;

        encoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(), enableIntelVp8Encoder, enableH264HighProfile);
        decoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());

        PeerConnectionFactory factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
        Log.d(TAG, "Peer connection factory created.");
        adm.release();

        Log.d(TAG, "Peer connection factory created.");
        return factory;
    }


    AudioDeviceModule createJavaAudioDevice() {
        JavaAudioDeviceModule.AudioTrackErrorCallback audioTrackErrorCallback = new JavaAudioDeviceModule.AudioTrackErrorCallback() {
            @Override
            public void onWebRtcAudioTrackInitError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackInitError: " + errorMessage);
                reportError(errorMessage);
            }

            @Override
            public void onWebRtcAudioTrackStartError(
                    JavaAudioDeviceModule.AudioTrackStartErrorCode errorCode, String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackStartError: " + errorCode + ". " + errorMessage);
                reportError(errorMessage);
            }

            @Override
            public void onWebRtcAudioTrackError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackError: " + errorMessage);
                reportError(errorMessage);
            }
        };

        return JavaAudioDeviceModule.builder(context)
                .setAudioTrackErrorCallback(audioTrackErrorCallback)
                .createAudioDeviceModule();
    }

    private void reportError(final String errorMessage) {
        Log.e(TAG, "Peerconnection error: " + errorMessage);
    }


}