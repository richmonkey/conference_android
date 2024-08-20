package com.beetle.conference;

import androidx.annotation.Nullable;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mediasoup.Device;
import org.mediasoup.Fingerprint;
import org.mediasoup.MediaSoupClient;
import org.mediasoup.RecvTransport;
import org.mediasoup.SendTransport;
import org.mediasoup.Transport;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import protooclient.Peer;
import protooclient.PeerListener;
import protooclient.Notification;
import protooclient.Request;
import protooclient.Response;


class RoomClient {
    private static final String TAG = "RoomActivity";
    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";

    static AtomicInteger predicate = new AtomicInteger();

    final protected String token;
    final protected String displayName;
    protected boolean cameraOn = true;
    protected boolean microphoneOn = true;

    PeerConnection.RTCConfiguration rtcConfig;
    protected EglBase rootEglBase;
    SurfaceTextureHelper surfaceTextureHelper;
    protected PeerConnectionFactory pcFactory;
    Device device;
    SendTransport sendTransport;
    RecvTransport recvTransport;

    //Ready after send&recv transport created.
    boolean initialized = false;

    int nextId;
    Peer peer;

    Handler handler;

    HashMap<Long, PendingRequest> pendingRequests = new HashMap<>();

    VideoCapturer videoCapturer;
    VideoSource videoSource;
    VideoTrack videoTrack;

    AudioSource audioSource;
    AudioTrack audioTrack;

    Producer videoProducer;
    Producer audioProducer;

    HashMap<String, Consumer> consumers = new HashMap<>();

    final Context appContext;

    final GetVideoRenderer getVideoRenderer;
    final ReleaseVideoRenderer releaseVideoRenderer;

    public interface GetVideoRenderer {
        SurfaceViewRenderer createRenderer(String id, boolean isLocal);
    }

    public interface ReleaseVideoRenderer {
        void removeRenderer(String id);
    }

    interface ResponseHandler {
        void onSuccess(Response resp);
        void onError(Response resp);
    }

    static class PendingRequest {
        public Request reqest;
        public ResponseHandler handler;
        public PendingRequest(Request req, ResponseHandler handler) {
            this.reqest = req;
            this.handler = handler;
        }
    }


    public static class Producer {
        public String id;
        public String localId;
        public RtpSender rtpSender;
        public MediaStreamTrack track;
        public JSONObject rtpParameters;
    }

    public static class Consumer {
        public String id;
        public String localId;
        public String producerId;
        public RtpReceiver rtpReceiver;
        public MediaStreamTrack track;
        public JSONObject rtpParameters;
        public String peerId;
    }

    class PeerListenerImpl implements PeerListener {
        public void onClose() {

        }
        public void onDisconnected() {

        }
        public void onFailed() {

        }
        public void onNotification(Notification p0) {
            String method = p0.getMethod();
            try {
                Log.i(TAG, "handle notification:" + method + " data:" + p0.getData());

                if (method.equals("newPeer")) {
                    JSONObject object = new JSONObject(p0.getData());
                    String peerId = object.getString("peerId");
                    String displayName = object.getString("displayName");

                } else if (method.equals("peerClosed")) {
                    JSONObject object = new JSONObject(p0.getData());
                    String peerId = object.getString("peerId");

                } else if (method.equals("newProducer")) {
                    JSONObject object = new JSONObject(p0.getData());
                    String id = object.getString("id");
                    String kind = object.getString("kind");
                    String peerId = object.getString("peerId");

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            consumeProducer(id, peerId);
                        }
                    });
                } else if (method.equals("consumerClosed")) {
                    JSONObject object = new JSONObject(p0.getData());
                    String consumerId = object.getString("consumerId");

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Consumer consumer = consumers.get(consumerId);
                            if (consumer == null) {
                                return;
                            }
                            recvTransport.closeConsumer(consumer.localId);
                            consumers.remove(consumerId);
                            if (consumer.track.kind().equals("video")) {
                                removeRenderer(consumer.id);
                            }
                        }
                    });
                } else if (method.equals("consumerPaused")) {
                    JSONObject object = new JSONObject(p0.getData());
                    String consumerId = object.getString("consumerId");
                    handler.post(new Runnable() {
                        @Override
                        public void run() {

                        }
                    });
                } else if (method.equals("consumerResumed")) {
                    JSONObject object = new JSONObject(p0.getData());
                    String consumerId = object.getString("consumerId");
                } else {
                    Log.i(TAG, "unhandled notification:" + method + " data:" + p0.getData());
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        public void onOpen() {
            Log.i(TAG, "on open");
            resetNextId();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    auth();
                }
            });
        }

        public void onRequest(Request p0) {
            Log.i(TAG, "on request");
        }

        public void onResponse(Response resp) {
            Log.i(TAG, "on response:" + resp.getId() + " " + resp.getData() + " " + resp.getOk());
            if (!resp.getOk()) {
                Log.w(TAG, "on response err:" + " " + resp.getErrorCode() + " " + resp.getErrorReason());
            }

            handler.post(() -> {
               PendingRequest pendingRequest = pendingRequests.get(resp.getId());
               if (pendingRequest == null) {
                   Log.w(TAG, "Can't find request with response id:" + resp.getId());
                   return;
               }
               pendingRequests.remove(resp.getId());
               if (resp.getOk()) {
                   pendingRequest.handler.onSuccess(resp);
               } else {
                   pendingRequest.handler.onError(resp);
               }
            });
        }
    }

    public RoomClient(Context appContext, GetVideoRenderer getVideoRenderer, ReleaseVideoRenderer releaseVideoRenderer, String token, String displayName) {
        this.token = token;
        this.displayName = displayName;
        this.appContext = appContext;
        this.getVideoRenderer = getVideoRenderer;
        this.releaseVideoRenderer = releaseVideoRenderer;

        if (predicate.getAndIncrement()==0) {
            Log.d(TAG, "Initialize WebRTC");
            PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(appContext)
                            .createInitializationOptions());

            MediaSoupClient.initialize();
        }

        Log.i(TAG, "mediasoup version:" + MediaSoupClient.version());

        rootEglBase = EglBase.create();
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        pcFactory = createPeerConnectionFactory(options, rootEglBase);


        rtcConfig = new PeerConnection.RTCConfiguration(new ArrayList<>());
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        handler = new Handler();
    }

    public boolean isCameraOn() {
        return cameraOn;
    }

    public void setCameraOn(boolean cameraOn) {
        this.cameraOn = cameraOn;
    }

    public boolean isMicrophoneOn() {
        return this.microphoneOn;
    }

    public void setMicrophoneOn(boolean microphoneOn) {
        this.microphoneOn = microphoneOn;
    }

    public EglBase getRootEglBase() {
        return rootEglBase;
    }

    public VideoCapturer getVideoCapturer() {
        return videoCapturer;
    }
    public void start(String protooUrl) {
        Log.i(TAG, "open peer");
        peer = new Peer(protooUrl, new PeerListenerImpl());
        peer.open();
    }

    public void stop() {
        if (peer != null) {
            peer.close();
            peer = null;
        }

        if (sendTransport != null) {
            sendTransport.close();
            sendTransport = null;
        }
        if (recvTransport != null) {
            recvTransport.close();
            recvTransport = null;
        }

        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }

        if (videoTrack != null) {
            videoTrack.dispose();
            videoTrack = null;
        }

        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (audioTrack != null) {
            audioTrack.dispose();
            audioTrack = null;
        }

        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }

        if (pcFactory != null) {
            pcFactory.dispose();
            pcFactory = null;
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

    private void auth() {
        try {
            JSONObject j = new JSONObject();
            j.put("token", token);
            this.request("auth", j, new ResponseHandler() {
                @Override
                public void onSuccess(Response resp) {
                    Log.i(TAG, "auth success");
                    getRouterRtpCapabilities();
                }

                @Override
                public void onError(Response resp) {

                }


            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getRouterRtpCapabilities() {
        request("getRouterRtpCapabilities", new ResponseHandler() {
            @Override
            public void onSuccess(Response resp) {
                loadDevice(resp.getData());
                createSendTransport();
            }

            @Override
            public void onError(Response resp) {

            }
        });
    }

    private void createSendTransport() {
        Log.i(TAG, "create send transport");
        try {
            JSONObject object = new JSONObject();
            // Todo put sctpCapabilities
            object.put("forceTcp", false);
            object.put("producing", true);
            object.put("consuming", false);

            request("createWebRtcTransport", object, new ResponseHandler() {
                @Override
                public void onSuccess(Response resp) {
                    try {
                        JSONObject object = new JSONObject(resp.getData());
                        String id = object.getString("id");
                        Object iceParameters = object.get("iceParameters");
                        Object iceCandidates = object.get("iceCandidates");
                        Object dtlsParameters = object.get("dtlsParameters");
                        Log.i(TAG, "iceParameters:" + iceParameters);
                        Log.i(TAG, "iceCandidates:" + iceCandidates);
                        Log.i(TAG, "dtlsParameters:" + dtlsParameters);

                        sendTransport = device.createSendTransport(id, iceParameters.toString(), iceCandidates.toString(), dtlsParameters.toString(), rtcConfig, pcFactory);

                        connectTransport(sendTransport, "server", new ResponseHandler() {
                            @Override
                            public void onSuccess(Response resp) {
                                Log.i(TAG, "send transport connect success");
                                createRecvTransport();
                            }

                            @Override
                            public void onError(Response resp) {

                            }
                        });

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onError(Response resp) {

                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    private void createRecvTransport() {
        Log.i(TAG, "create recv transport");
        try {
            JSONObject object = new JSONObject();
            // Todo put sctpCapabilities
            object.put("forceTcp", false);
            object.put("producing", false);
            object.put("consuming", true);

            request("createWebRtcTransport", object, new ResponseHandler() {
                @Override
                public void onSuccess(Response resp) {
                    try {
                        JSONObject object = new JSONObject(resp.getData());
                        String id = object.getString("id");
                        Object iceParameters = object.get("iceParameters");
                        Object iceCandidates = object.get("iceCandidates");
                        Object dtlsParameters = object.get("dtlsParameters");
                        recvTransport = device.createRecvTransport(id, iceParameters.toString(), iceCandidates.toString(), dtlsParameters.toString(), rtcConfig, pcFactory);
                        connectTransport(recvTransport, "client", new ResponseHandler() {
                            @Override
                            public void onSuccess(Response resp) {
                                Log.i(TAG, "recv transport connect success");
                                join();
                            }

                            @Override
                            public void onError(Response resp) {

                            }
                        });
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onError(Response resp) {

                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /* Post dtlsparameters to server.
    **    Params:
    **         localDtlsRole: sendTransport with "server" or recvTransport with "client"
    */
    private void connectTransport(Transport transport, String localDtlsRole, ResponseHandler handler) {

        try {
            JSONObject fingerprint = new JSONObject();
            Fingerprint fp = transport.getFingerprint();
            fingerprint.put("algorithm", fp.algorithm);
            fingerprint.put("value", fp.fingerprint);

            JSONArray fingerprints = new JSONArray();
            fingerprints.put(fingerprint);

            JSONObject dtlsParameters = new JSONObject();
            dtlsParameters.put("role", localDtlsRole);
            dtlsParameters.put("fingerprints", fingerprints);

            JSONObject object = new JSONObject();
            object.put("transportId", transport.getId());
            object.put("dtlsParameters", dtlsParameters);

            request("connectWebRtcTransport", object, handler);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void join() {
        try {
            JSONObject rtpCaps = new JSONObject(device.getRtpCapabilities());

            JSONObject device = new JSONObject();
            device.put("flag", "android-native");
            device.put("name", "android");
            device.put("version", "113");

            JSONObject object = new JSONObject();
            object.put("displayName", displayName);
            object.put("device", device);
            object.put("produceVideo", true);
            object.put("produceAudio", true);
            object.put("rtpCapabilities", rtpCaps);

            request("join", object, new ResponseHandler() {
                @Override
                public void onSuccess(Response resp) {
                    try {
                        produceAudio();
                        produceVideo();

                        //Consume all producers from other peers.
                        JSONObject object = new JSONObject(resp.getData());
                        JSONArray peers = object.getJSONArray("peers");
                        for (int i = 0; i < peers.length();i ++) {
                            JSONObject peer = peers.getJSONObject(i);
                            String peerId = peer.getString("id");
                            JSONArray producers = peer.getJSONArray("producers");
                            for (int j = 0; j < producers.length(); j++) {
                                JSONObject producer = producers.getJSONObject(j);
                                String producerId = producer.getString("id");
                                consumeProducer(producerId, peerId);
                            }
                        }

                        initialized = true;
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onError(Response resp) {

                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    protected void produceVideo() {
        if (!device.canProduce("video")) {
            Log.w(TAG, "Device can't produce video");
            return;
        }

        int cameraPermission = appContext.checkSelfPermission(Manifest.permission.CAMERA);
        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "camera permission denied");
            return;
        }

        SurfaceViewRenderer localRenderer = createRenderer("local", true);
        VideoSource videoSource = pcFactory.createVideoSource(false);
        VideoTrack videoTrack = createVideoTrack(videoSource, localRenderer);
        VideoCapturer videoCapturer = createVideoCapturer(videoSource);
        if (videoCapturer == null) {
            Log.w(TAG, "Create video capturer failure.");
            return;
        }

        try {
            JSONObject codecOptions = new JSONObject();
            codecOptions.put("videoGoogleStartBitrate", 1000);

            List<RtpParameters.Encoding> encodings = new ArrayList<>();
            SendTransport.SendResult sendResult = sendTransport.produce(videoTrack, encodings, codecOptions.toString(), null);

            JSONObject rtpParameters = new JSONObject(sendResult.rtpParameters);

            Producer producer = new Producer();
            producer.localId = sendResult.localId;
            producer.rtpSender = sendResult.rtpSender;
            producer.track = sendResult.rtpSender.track();
            producer.rtpParameters = rtpParameters;

            this.videoProducer = producer;
            this.videoCapturer = videoCapturer;
            this.videoSource = videoSource;
            this.videoTrack = videoTrack;

            int videoWidth = 640;
            int videoHeight = 480;
            int videoFps = 30;
            videoCapturer.startCapture(videoWidth, videoHeight, videoFps);

            JSONObject object = new JSONObject();
            object.put("transportId", sendTransport.getId());
            object.put("kind", "video");
            object.put("rtpParameters", rtpParameters);

            request("produce", object, new ResponseHandler() {
                @Override
                public void onSuccess(Response resp) {
                    try {
                        JSONObject object = new JSONObject(resp.getData());
                        producer.id = object.getString("id");
                    } catch(JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onError(Response resp) {

                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    protected void produceAudio() {
        if (!device.canProduce("audio")) {
            Log.w(TAG, "Device can't produce audio");
            return;
        }

        int recordPermission = (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
        if (recordPermission != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "record audio permission denied");
            return;
        }

        AudioSource audioSource = createAudioSource();
        AudioTrack audioTrack = createAudioTrack(audioSource);
        try {
            JSONObject codecOptions = new JSONObject();
            codecOptions.put("opusStereo", true);
            codecOptions.put("opusDtx", true);

            List<RtpParameters.Encoding> encodings = new ArrayList<>();
            SendTransport.SendResult sendResult = sendTransport.produce(audioTrack, encodings, codecOptions.toString(), null);

            JSONObject rtpParameters = new JSONObject(sendResult.rtpParameters);
            Producer producer = new Producer();
            producer.localId = sendResult.localId;
            producer.rtpSender = sendResult.rtpSender;
            producer.track = sendResult.rtpSender.track();
            producer.rtpParameters = rtpParameters;

            this.audioProducer = producer;
            this.audioSource = audioSource;
            this.audioTrack = audioTrack;

            JSONObject object = new JSONObject();
            object.put("transportId", sendTransport.getId());
            object.put("kind", "audio");
            object.put("rtpParameters", rtpParameters);

            request("produce", object, new ResponseHandler() {
                @Override
                public void onSuccess(Response resp) {
                    try {
                        JSONObject object = new JSONObject(resp.getData());
                        producer.id = object.getString("id");
                    } catch(JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onError(Response resp) {

                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    protected void closeVideoProducer() {
        if (videoProducer == null) {
            return;
        }
        try {
            videoCapturer.stopCapture();
            removeRenderer("local");

            this.sendTransport.closeProducer(videoProducer.localId);

            JSONObject object = new JSONObject();
            object.put("producerId", videoProducer.id);
            request("closeProducer", object, new ResponseHandler() {
                @Override
                public void onSuccess(Response resp) {
                }
                @Override
                public void onError(Response resp) {

                }
            });
            videoCapturer.dispose();
            videoCapturer = null;
            videoTrack.dispose();
            videoTrack = null;
            videoSource.dispose();
            videoSource = null;
            videoProducer = null;

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    protected void closeAudioProducer() {
        if (audioProducer == null) {
            return;
        }

        this.sendTransport.closeProducer(audioProducer.localId);
        try {
            JSONObject object = new JSONObject();
            object.put("producerId", audioProducer.id);
            request("closeProducer", object, new ResponseHandler() {
                @Override
                public void onSuccess(Response resp) {
                }

                @Override
                public void onError(Response resp) {

                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }

        audioTrack.dispose();
        audioTrack = null;
        audioSource.dispose();
        audioSource = null;
        audioProducer = null;
    }

    private void consumeProducer(String producerId, String peerId) {
        String transportId = recvTransport.getId();
        try {
            JSONObject object = new JSONObject();
            object.put("producerId", producerId);
            object.put("transportId", transportId);

            request("consume", object, new ResponseHandler() {
                @Override
                public void onSuccess(Response resp) {
                    try {
                        JSONObject object = new JSONObject(resp.getData());
                        String id = object.getString("id");
                        String kind = object.getString("kind");
                        String type = object.getString("type");
                        boolean producerPaused = object.getBoolean("producerPaused");
                        JSONObject rtpParameters = object.getJSONObject("rtpParameters");
                        Log.i(TAG, "transport id:" + transportId + " producer id:" + producerId + " consumer:" + resp.getData());
                        RecvTransport.RecvResult recvResult = recvTransport.consume(id, producerId, kind, rtpParameters.toString());
                        if (kind.equals("video")) {
                            SurfaceViewRenderer renderer = createRenderer(id, false);
                            VideoTrack videoTrack = (VideoTrack)recvResult.track;
                            videoTrack.addSink(renderer);
                        }
                        Consumer consumer = new Consumer();
                        consumer.id = id;
                        consumer.localId = recvResult.localId;
                        consumer.producerId = producerId;
                        consumer.rtpReceiver = recvResult.rtpReceiver;
                        consumer.track = recvResult.track;
                        consumer.rtpParameters = rtpParameters;
                        consumer.peerId = peerId;
                        consumers.put(id, consumer);
                        resumeConsumer(consumer);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onError(Response resp) {

                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void resumeConsumer(Consumer consumer) {
        try {
            JSONObject object = new JSONObject();
            object.put("consumerId", consumer.id);
            request("resumeConsumer", object, new ResponseHandler() {
                @Override
                public void onSuccess(Response resp) {

                }

                @Override
                public void onError(Response resp) {

                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    protected SurfaceViewRenderer createRenderer(String id, boolean isLocal) {
        return this.getVideoRenderer.createRenderer(id, isLocal);
    }

    protected void removeRenderer(String id) {
        this.releaseVideoRenderer.removeRenderer(id);
    }

    private void loadDevice(String routerRtpCaps) {
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

        device = new Device();
        device.load(routerRtpCaps, rtcConfig, pcFactory);
    }

    private void request(String method, ResponseHandler handler) {
        request(method, "{}", handler);
    }
    private void request(String method, JSONObject data, ResponseHandler handler) {
        request(method, data.toString(), handler);
    }

    private void request(String method, String data, ResponseHandler handler) {
        try {
            Request req = new Request(generateNextId(), method, data);
            peer.request(req);
            pendingRequests.put(req.getId(), new PendingRequest(req, handler));
            Log.i(TAG, "Post request:" + req.getId() + " method:" + method + " data: " + data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void resetNextId() {
        nextId = 0;
    }

    private long generateNextId() {
        nextId += 1;
        return nextId;
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

        return JavaAudioDeviceModule.builder(appContext)
                .setAudioTrackErrorCallback(audioTrackErrorCallback)
                .createAudioDeviceModule();
    }

    private VideoCapturer createVideoCapturer(VideoSource videoSource) {
        VideoCapturer videoCapturer = null;
        if (useCamera2()) {
            Log.d(TAG, "Creating capturer using camera2 API.");
            videoCapturer = createCameraCapturer(new Camera2Enumerator(appContext));
        } else {
            Log.d(TAG, "Creating capturer using camera1 API.");
            videoCapturer = createCameraCapturer(new Camera1Enumerator(true));
        }
        if (videoCapturer == null) {
            reportError("Failed to open camera");
            return null;
        }
        videoCapturer.initialize(surfaceTextureHelper, appContext, videoSource.getCapturerObserver());
        return videoCapturer;
    }

    private boolean useCamera2() {
        return Camera2Enumerator.isSupported(appContext);
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Log.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Log.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    @Nullable
    private VideoTrack createVideoTrack(VideoSource videoSource, SurfaceViewRenderer localRender) {
        VideoTrack localVideoTrack = pcFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        localVideoTrack.setEnabled(true);
        if (localRender != null) {
            localVideoTrack.addSink(localRender);
        }
        return localVideoTrack;
    }

    private AudioSource createAudioSource() {
        MediaConstraints audioConstraints = new MediaConstraints();
        AudioSource audioSource = pcFactory.createAudioSource(audioConstraints);
        return audioSource;
    }

    private AudioTrack createAudioTrack(AudioSource audioSource) {
        AudioTrack localAudioTrack = pcFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        return localAudioTrack;
    }

    private void reportError(final String errorMessage) {
        Log.e(TAG, "Peerconnection error: " + errorMessage);
    }


}