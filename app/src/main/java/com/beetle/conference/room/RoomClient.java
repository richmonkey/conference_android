package com.beetle.conference.room;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
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
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpParameters;
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
import java.util.function.BiConsumer;

import protooclient.Peer;
import protooclient.Request;
import protooclient.Response;


public class RoomClient {
    static final String TAG = "RoomActivity";
    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";

    static AtomicInteger predicate = new AtomicInteger();

    final String token;
    final String displayName;

    PeerConnection.RTCConfiguration rtcConfig;
    EglBase rootEglBase;
    SurfaceTextureHelper surfaceTextureHelper;
    PeerConnectionFactory pcFactory;
    Device device;
    SendTransport sendTransport;
    RecvTransport recvTransport;

    //Ready after send&recv transport created.
    boolean initialized = false;

    int nextId;
    Peer peer;

    Handler handler;

    HashMap<Long, PendingRequest> pendingRequests = new HashMap<>();

    HashMap<String, Consumer> consumers = new HashMap<>();

    ArrayList<Producer> producers = new ArrayList<>();

    final VideoRendererDelegate videoRendererDelegate;
    //final Context appContext;

    final RoomClientObserver observer;


    /*
     * Peer callbacks live in RoomPeerListener so the signaling adapter remains
     * separate from room session orchestration.
     */
    public RoomClient(Context appContext,
                      RoomClientObserver observer,
                      VideoRendererDelegate videoRendererDelegate,
                      String token,
                      String displayName) {
        this.token = token;
        this.displayName = displayName;

        this.observer = observer;
        this.videoRendererDelegate = videoRendererDelegate;

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
        pcFactory = createPeerConnectionFactory(options, rootEglBase, appContext);


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

        handler = new Handler(Looper.myLooper());
    }

    public EglBase getRootEglBase() {
        return rootEglBase;
    }


    public void start(String protooUrl) {
        Log.i(TAG, "open peer");
        peer = new Peer(protooUrl, new RoomPeerListener(this));
        peer.open();
    }

    public void stop() {
        if (peer != null) {
            peer.close();
            peer = null;
        }

        consumers.forEach(new BiConsumer<String, Consumer>() {
            @Override
            public void accept(String s, Consumer consumer) {
                consumer.close();
            }
        });
        consumers.clear();

        if (sendTransport != null) {
            sendTransport.close();
            sendTransport = null;
        }
        if (recvTransport != null) {
            recvTransport.close();
            recvTransport = null;
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

    void auth() {
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

    public SurfaceViewRenderer createRenderer(Context context, boolean isLocal) {
        SurfaceViewRenderer render = new org.webrtc.SurfaceViewRenderer(context);
        render.init(getRootEglBase().getEglBaseContext(), null);
        if (isLocal) {
            render.setZOrderMediaOverlay(true);
            render.setMirror(true);//default front camera
            render.getHolder().setFormat(PixelFormat.TRANSPARENT);
        }
        return render;
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
                        initialized = true;

                        //Consume all producers from other peers.
                        JSONObject object = new JSONObject(resp.getData());
                        JSONArray peers = object.getJSONArray("peers");

                        ArrayList<String> peerIds = new ArrayList<>();
                        for (int i = 0; i < peers.length();i ++) {
                            JSONObject peer = peers.getJSONObject(i);
                            String peerId = peer.getString("id");
                            peerIds.add(peerId);
                        }

                        observer.onJoined(peerIds);

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

    public void produceVideo(Context appContext, ProduceCallback cb) {
        if (!device.canProduce("video")) {
            Log.w(TAG, "Device can't produce video");
            cb.onError();
            return;
        }

        int cameraPermission = appContext.checkSelfPermission(Manifest.permission.CAMERA);
        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "camera permission denied");
            cb.onError();
            return;
        }

        SurfaceViewRenderer renderer = videoRendererDelegate.createRenderer("local", true);
        VideoSource videoSource = pcFactory.createVideoSource(false);
        VideoTrack videoTrack = createVideoTrack(videoSource, renderer);
        VideoCapturer videoCapturer = createVideoCapturer(videoSource, appContext);
        if (videoCapturer == null) {
            Log.w(TAG, "Create video capturer failure.");
            cb.onError();
            return;
        }

        try {
            JSONObject codecOptions = new JSONObject();
            codecOptions.put("videoGoogleStartBitrate", 1000);

            List<RtpParameters.Encoding> encodings = new ArrayList<>();
            SendTransport.SendResult sendResult = sendTransport.produce(videoTrack, encodings, codecOptions.toString(), null);
            JSONObject rtpParameters = new JSONObject(sendResult.rtpParameters);

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
                        String id = object.getString("id");

                        Producer producer = new Producer(id, sendResult.localId, sendResult.rtpSender,
                                sendResult.rtpSender.track(), rtpParameters, "video",
                                videoSource, videoCapturer, sendTransport);
                        cb.onSuccess(producer);
                        producers.add(producer);
                    } catch(JSONException e) {
                        e.printStackTrace();
                        cb.onError();
                    }
                }

                @Override
                public void onError(Response resp) {
                    cb.onError();
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            cb.onError();
        }

    }

    public void produceAudio(Context appContext, boolean muted, ProduceCallback cb) {
        if (!device.canProduce("audio")) {
            Log.w(TAG, "Device can't produce audio");
            cb.onError();
            return;
        }

        int recordPermission = (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
        if (recordPermission != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "record audio permission denied");
            cb.onError();
            return;
        }

        AudioSource audioSource = createAudioSource();
        AudioTrack audioTrack = createAudioTrack(audioSource);
        audioTrack.setEnabled(!muted);
        try {
            JSONObject codecOptions = new JSONObject();
            codecOptions.put("opusStereo", true);
            codecOptions.put("opusDtx", true);

            List<RtpParameters.Encoding> encodings = new ArrayList<>();
            SendTransport.SendResult sendResult = sendTransport.produce(audioTrack, encodings, codecOptions.toString(), null);
            JSONObject rtpParameters = new JSONObject(sendResult.rtpParameters);
            JSONObject object = new JSONObject();
            object.put("transportId", sendTransport.getId());
            object.put("kind", "audio");
            object.put("rtpParameters", rtpParameters);

            request("produce", object, new ResponseHandler() {
                @Override
                public void onSuccess(Response resp) {
                    try {
                        JSONObject object = new JSONObject(resp.getData());
                        String id = object.getString("id");
                        Producer producer = new Producer(id, sendResult.localId,
                                sendResult.rtpSender, sendResult.rtpSender.track(), rtpParameters,
                                "audio", audioSource, sendTransport);
                        cb.onSuccess(producer);
                        producers.add(producer);
                    } catch(JSONException e) {
                        e.printStackTrace();
                        cb.onError();
                    }
                }

                @Override
                public void onError(Response resp) {
                    cb.onError();
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            cb.onError();
        }
    }

    public boolean switchCamera() {
        Producer producer = findProducer("video");
        for (int i = 0; i < producers.size(); i++) {
            if (producers.get(i).kind.equals("video")) {
                producer = producers.get(i);
                break;
            }
        }
        if (producer == null) {
            return false;
        }

        VideoCapturer videoCapturer = producer.getVideoCapturer();
        if (videoCapturer instanceof CameraVideoCapturer) {
            Log.d(TAG, "Switch camera");
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
            cameraVideoCapturer.switchCamera(null);
            return true;
        } else {
            Log.d(TAG, "Will not switch camera, video caputurer is not a camera");
            return false;
        }
    }

    public void applyMute(boolean muted) {
        Producer producer = findProducer("audio");
        if (producer != null) {
            AudioTrack track = (AudioTrack) producer.getTrack();
            track.setEnabled(!muted);
        }
    }

    private void closeProducer(Producer producer) {
        try {
            JSONObject object = new JSONObject();
            object.put("producerId", producer.id);
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
    }

    public void closeAudioProducer() {
        Producer audioProducer = findProducer("audio");
        if (audioProducer != null) {
            audioProducer.close();
            closeProducer(audioProducer);
            producers.remove(audioProducer);
        }
    }

    public void closeVideoProducer() {
        Producer videoProducer = findProducer("video");
        if (videoProducer != null) {
            videoProducer.close();
            closeProducer(videoProducer);
            producers.remove(videoProducer);
            videoRendererDelegate.removeRenderer("local");
        }
    }

    private Producer findProducer(String kind) {
        for (int i = 0; i < producers.size(); i++) {
            Producer p = producers.get(i);
            if (p.kind.equals(kind)) {
                return p;
            }
        }
        return null;
    }


    void consumeProducer(String producerId, String peerId) {
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
                        Log.i(TAG, "transport id:" + transportId +
                                " producer id:" + producerId +
                                " consumer:" + resp.getData() +
                                " type:" + type +
                                " producer paused:" +  producerPaused);
                        RecvTransport.RecvResult recvResult = recvTransport.consume(id, producerId, kind, rtpParameters.toString());
                        Consumer consumer = new Consumer(id, recvResult.localId, producerId,
                                recvResult.rtpReceiver, recvResult.track, rtpParameters,
                                kind, peerId, recvTransport);
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

    public void closeConsumer(Consumer consumer) {
        try {
            JSONObject object = new JSONObject();
            object.put("consumerId", consumer.id);
            request("closeConsumer", object, new ResponseHandler() {
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

    void resetNextId() {
        nextId = 0;
    }

    private long generateNextId() {
        nextId += 1;
        return nextId;
    }

    public PeerConnectionFactory createPeerConnectionFactory(PeerConnectionFactory.Options options, EglBase rootEglBase, Context appContext) {
        AudioDeviceModule adm = createJavaAudioDevice(appContext);
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


    AudioDeviceModule createJavaAudioDevice(Context appContext) {
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

    private VideoCapturer createVideoCapturer(VideoSource videoSource, Context appContext) {
        VideoCapturer videoCapturer = null;
        if (useCamera2(appContext)) {
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

    private boolean useCamera2(Context appContext) {
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
        return pcFactory.createAudioSource(audioConstraints);
    }

    private AudioTrack createAudioTrack(AudioSource audioSource) {
        return pcFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
    }

    private void reportError(final String errorMessage) {
        Log.e(TAG, "peer connection error: " + errorMessage);
    }
}
