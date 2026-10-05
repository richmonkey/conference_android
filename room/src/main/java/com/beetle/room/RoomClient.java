package com.beetle.room;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mediasoup.Device;
import org.mediasoup.Fingerprint;
import org.mediasoup.RecvTransport;
import org.mediasoup.SendTransport;
import org.mediasoup.Transport;
import org.webrtc.EglBase;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSink;
import org.webrtc.VideoTrack;
import org.webrtc.RtpSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import protooclient.Peer;
import protooclient.Request;
import protooclient.Response;


public class RoomClient {
    static final String TAG = "RoomActivity";
    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";
    public static final double DEFAULT_ACTIVE_SPEAKER_AUDIO_LEVEL_THRESHOLD = 0.1d;

    final String token;
    final String displayName;
    final RoomClientConfig config;

    WebRtcRuntime webRtcRuntime;
    LocalMediaController localMediaController;
    Device device;
    SendTransport sendTransport;
    RecvTransport recvTransport;

    private volatile RoomSessionState sessionState = RoomSessionState.IDLE;

    int nextId;
    Peer peer;

    Handler handler;

    HashMap<Long, PendingRequest> pendingRequests = new HashMap<>();

    HashMap<String, Consumer> consumers = new HashMap<>();

    ArrayList<Producer> producers = new ArrayList<>();

    final VideoRendererDelegate videoRendererDelegate;

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
        this(appContext, observer, videoRendererDelegate, token, displayName,
                RoomClientConfig.defaultConfig());
    }

    public RoomClient(Context appContext,
                      RoomClientObserver observer,
                      VideoRendererDelegate videoRendererDelegate,
                      String token,
                      String displayName,
                      RoomClientConfig config) {
        this.token = token;
        this.displayName = displayName;
        this.config = java.util.Objects.requireNonNull(config, "config");

        this.observer = observer;
        this.videoRendererDelegate = videoRendererDelegate;

        RoomSdk.requireInitialized();

        webRtcRuntime = new WebRtcRuntime(appContext);
        localMediaController = new LocalMediaController(webRtcRuntime, config.getVideoCapture());

        handler = new Handler(Looper.myLooper());
    }

    public EglBase getRootEglBase() {
        return webRtcRuntime.getRootEglBase();
    }

    public RoomSessionState getSessionState() {
        return sessionState;
    }

    public RoomClientConfig getConfig() {
        return config;
    }

    /**
     * Returns the IDs of remote peers whose audio level is at or above the
     * default active-speaker threshold. Call periodically to refresh UI state.
     */
    public List<String> detectActiveSpeakerPeerIds() {
        return detectActiveSpeakerPeerIds(DEFAULT_ACTIVE_SPEAKER_AUDIO_LEVEL_THRESHOLD);
    }

    /**
     * Returns the IDs of remote peers whose audio level is at or above
     * {@code audioLevelThreshold}. WebRTC audio levels range from 0 to 1.
     */
    public List<String> detectActiveSpeakerPeerIds(double audioLevelThreshold) {
        if (audioLevelThreshold < 0d || audioLevelThreshold > 1d) {
            throw new IllegalArgumentException("audioLevelThreshold must be between 0 and 1");
        }

        Set<String> activePeerIds = new LinkedHashSet<>();
        for (Consumer consumer : consumers.values()) {
            if (!MediaKind.AUDIO.wireValue().equals(consumer.kind)
                    || consumer.peerId == null || consumer.peerId.isEmpty()) {
                continue;
            }

            RtpReceiver receiver = consumer.getRtpReceiver();
            if (receiver == null) {
                continue;
            }

            for (RtpSource source : receiver.getSources()) {
                if (source.getSourceType() != RtpSource.Type.SSRC) {
                    continue;
                }

                Double audioLevel = source.getAudioLevel();
                if (audioLevel == null || audioLevel < audioLevelThreshold) {
                    continue;
                }

                if (activePeerIds.add(consumer.peerId)) {
                    Log.i(TAG, "Peer " + consumer.peerId + " is speaking");
                }
                break;
            }
        }
        return activePeerIds.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(activePeerIds);
    }

    public void start(String protooUrl) {
        if (!transitionTo(RoomSessionState.CONNECTING, RoomSessionState.IDLE)) {
            Log.w(TAG, "Ignoring start in state " + sessionState);
            return;
        }
        Log.i(TAG, "open peer");
        try {
            peer = new Peer(protooUrl, new RoomPeerListener(this));
            peer.open();
        } catch (RuntimeException e) {
            failSession("open peer", e);
        }
    }

    public void stop() {
        if (sessionState == RoomSessionState.CLOSED || sessionState == RoomSessionState.CLOSING) {
            return;
        }
        transitionTo(RoomSessionState.CLOSING, RoomSessionState.IDLE, RoomSessionState.CONNECTING,
                RoomSessionState.RECONNECTING, RoomSessionState.AUTHENTICATING, RoomSessionState.CREATING_TRANSPORTS,
                RoomSessionState.JOINING, RoomSessionState.JOINED, RoomSessionState.FAILED);
        releaseAllResources();
        transitionTo(RoomSessionState.CLOSED, RoomSessionState.CLOSING);
    }

    private void releaseAllResources() {
        if (peer != null) {
            peer.close();
            peer = null;
        }
        releaseRoomResources();
        if (webRtcRuntime != null) {
            webRtcRuntime.release();
            webRtcRuntime = null;
            localMediaController = null;
        }
    }

    /** Releases resources owned by the current Peer connection but keeps automatic reconnection possible. */
    private void releaseRoomResources() {
        for (Producer producer : producers) {
            if (producer.kind.equals(MediaKind.VIDEO.wireValue())) {
                videoRendererDelegate.removeRenderer("local");
            }
            producer.close();
        }
        producers.clear();

        consumers.forEach(new BiConsumer<String, Consumer>() {
            @Override
            public void accept(String s, Consumer consumer) {
                if (consumer.kind.equals(MediaKind.VIDEO.wireValue())) {
                    videoRendererDelegate.removeRenderer(consumer.id);
                }
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
        if (device != null) {
            device.dispose();
            device = null;
        }
        pendingRequests.clear();
        nextId = 0;
    }

    private synchronized boolean transitionTo(RoomSessionState target,
                                              RoomSessionState... allowedSources) {
        for (RoomSessionState allowedSource : allowedSources) {
            if (sessionState == allowedSource) {
                Log.i(TAG, "Room session state: " + sessionState + " -> " + target);
                sessionState = target;
                return true;
            }
        }
        Log.w(TAG, "Ignoring invalid room session transition: " + sessionState + " -> " + target);
        return false;
    }

    private boolean isInState(RoomSessionState expected) {
        return sessionState == expected;
    }

    void handlePeerOpened() {
        if (!transitionTo(RoomSessionState.AUTHENTICATING, RoomSessionState.CONNECTING,
                RoomSessionState.RECONNECTING)) {
            return;
        }
        resetNextId();
        observer.onConnect();
        auth();
    }

    void handlePeerClosed() {
        observer.onClose();
    }

    void handlePeerDisconnected() {
        if (sessionState == RoomSessionState.CLOSED || sessionState == RoomSessionState.CLOSING
                || sessionState == RoomSessionState.FAILED) {
            return;
        }
        if (!transitionTo(RoomSessionState.RECONNECTING, RoomSessionState.CONNECTING,
                RoomSessionState.AUTHENTICATING, RoomSessionState.CREATING_TRANSPORTS,
                RoomSessionState.JOINING, RoomSessionState.JOINED)) {
            return;
        }
        observer.onDisconnect();

        releaseRoomResources();
    }

    void handlePeerFailed() {
        Log.w(TAG, "Peer connection attempt failed; waiting for automatic reconnect");
        transitionTo(RoomSessionState.RECONNECTING, RoomSessionState.CONNECTING);
    }

    private void failSession(String operation, Exception exception) {
        if (sessionState == RoomSessionState.CLOSED || sessionState == RoomSessionState.CLOSING) {
            return;
        }
        if (exception == null) {
            Log.e(TAG, "Room session failed: " + operation);
        } else {
            Log.e(TAG, "Room session failed: " + operation, exception);
        }
        if (transitionTo(RoomSessionState.FAILED, RoomSessionState.CONNECTING,
                RoomSessionState.RECONNECTING, RoomSessionState.AUTHENTICATING, RoomSessionState.CREATING_TRANSPORTS,
                RoomSessionState.JOINING, RoomSessionState.JOINED)) {
            observer.onError();
            releaseAllResources();
        }
    }

    void auth() {
        if (!isInState(RoomSessionState.AUTHENTICATING)) {
            return;
        }
        try {
            JSONObject j = new JSONObject();
            j.put("token", token);
            this.request("auth", j, new ResponseHandler() {
                @Override
                public void onSuccess(Response resp) {
                    if (!transitionTo(RoomSessionState.CREATING_TRANSPORTS,
                            RoomSessionState.AUTHENTICATING)) {
                        return;
                    }
                    Log.i(TAG, "auth success");
                    getRouterRtpCapabilities();
                }

                @Override
                public void onError(Response resp) {
                    failSession("authenticate", null);
                }


            });
        } catch (Exception e) {
            failSession("prepare authentication", e);
        }
    }

    public SurfaceViewRenderer createRenderer(Context context, boolean isLocal) {
        return webRtcRuntime.createRenderer(context, isLocal);
    }

    private void getRouterRtpCapabilities() {
        if (!isInState(RoomSessionState.CREATING_TRANSPORTS)) {
            return;
        }
        request("getRouterRtpCapabilities", new ResponseHandler() {
            @Override
            public void onSuccess(Response resp) {
                if (!isInState(RoomSessionState.CREATING_TRANSPORTS)) {
                    return;
                }
                loadDevice(resp.getData());
                createSendTransport();
            }

            @Override
            public void onError(Response resp) {
                failSession("get router RTP capabilities", null);
            }
        });
    }

    private void createSendTransport() {
        if (!isInState(RoomSessionState.CREATING_TRANSPORTS)) {
            return;
        }
        Log.i(TAG, "create send transport");
        try {
            request("createWebRtcTransport", new RoomProtocol.CreateTransportRequest(true, false), new ResponseHandler() {
                @Override
                public void onSuccess(Response resp) {
                    if (!isInState(RoomSessionState.CREATING_TRANSPORTS)) {
                        return;
                    }
                    try {
                        RoomProtocol.TransportResponse transport = RoomProtocol.TransportResponse.fromJson(resp.getData());
                        Log.i(TAG, "iceParameters:" + transport.iceParameters);
                        Log.i(TAG, "iceCandidates:" + transport.iceCandidates);
                        Log.i(TAG, "dtlsParameters:" + transport.dtlsParameters);

                        sendTransport = device.createSendTransport(transport.id, transport.iceParameters, transport.iceCandidates, transport.dtlsParameters,
                                webRtcRuntime.getRtcConfiguration(), webRtcRuntime.getPeerConnectionFactory());

                        connectTransport(sendTransport, DtlsRole.SERVER, new ResponseHandler() {
                            @Override
                            public void onSuccess(Response resp) {
                                Log.i(TAG, "send transport connect success");
                                createRecvTransport();
                            }

                            @Override
                            public void onError(Response resp) {
                                failSession("connect send transport", null);
                            }
                        });

                    } catch (JSONException e) {
                        failSession("parse send transport", e);
                    }
                }

                @Override
                public void onError(Response resp) {
                    failSession("create send transport", null);
                }
            });
        } catch (JSONException e) {
            failSession("prepare send transport", e);
        }
    }


    private void createRecvTransport() {
        if (!isInState(RoomSessionState.CREATING_TRANSPORTS)) {
            return;
        }
        Log.i(TAG, "create recv transport");
        try {
            request("createWebRtcTransport", new RoomProtocol.CreateTransportRequest(false, true), new ResponseHandler() {
                @Override
                public void onSuccess(Response resp) {
                    if (!isInState(RoomSessionState.CREATING_TRANSPORTS)) {
                        return;
                    }
                    try {
                        RoomProtocol.TransportResponse transport = RoomProtocol.TransportResponse.fromJson(resp.getData());
                        recvTransport = device.createRecvTransport(transport.id, transport.iceParameters, transport.iceCandidates, transport.dtlsParameters,
                                webRtcRuntime.getRtcConfiguration(), webRtcRuntime.getPeerConnectionFactory());
                        connectTransport(recvTransport, DtlsRole.CLIENT, new ResponseHandler() {
                            @Override
                            public void onSuccess(Response resp) {
                                Log.i(TAG, "recv transport connect success");
                                join();
                            }

                            @Override
                            public void onError(Response resp) {
                                failSession("connect receive transport", null);
                            }
                        });
                    } catch (JSONException e) {
                        failSession("parse receive transport", e);
                    }
                }

                @Override
                public void onError(Response resp) {
                    failSession("create receive transport", null);
                }
            });
        } catch (JSONException e) {
            failSession("prepare receive transport", e);
        }
    }

    /* Post dtlsparameters to server.
    **    Params:
    **         localDtlsRole: sendTransport with "server" or recvTransport with "client"
    */
    private void connectTransport(Transport transport, DtlsRole localDtlsRole, ResponseHandler handler) {
        try {
            Fingerprint fp = transport.getFingerprint();
            request("connectWebRtcTransport", new RoomProtocol.ConnectTransportRequest(
                    transport.getId(), localDtlsRole, fp.algorithm, fp.fingerprint), handler);
        } catch (JSONException e) {
            failSession("prepare transport connection", e);
        }
    }

    private void join() {
        if (!transitionTo(RoomSessionState.JOINING, RoomSessionState.CREATING_TRANSPORTS)) {
            return;
        }
        try {
            JSONObject rtpCaps = new JSONObject(device.getRtpCapabilities());

            request("join", new RoomProtocol.JoinRequest(displayName, config.getDeviceInfo(), rtpCaps), new ResponseHandler() {
                @Override
                public void onSuccess(Response resp) {
                    if (!transitionTo(RoomSessionState.JOINED, RoomSessionState.JOINING)) {
                        return;
                    }
                    try {
                        //Consume all producers from other peers.
                        RoomProtocol.JoinResponse joinResponse = RoomProtocol.JoinResponse.fromJson(resp.getData());

                        ArrayList<String> peerIds = new ArrayList<>();
                        for (RoomProtocol.Peer peer : joinResponse.peers) peerIds.add(peer.id);

                        observer.onJoined(peerIds);

                        for (RoomProtocol.Peer peer : joinResponse.peers) {
                            for (String producerId : peer.producerIds) consumeProducer(producerId, peer.id);
                        }
                    } catch (JSONException e) {
                        failSession("parse join response", e);
                    }
                }

                @Override
                public void onError(Response resp) {
                    failSession("join room", null);
                }
            });
        } catch (JSONException e) {
            failSession("prepare join", e);
        }
    }

    public void produceVideo(Context appContext, ProduceCallback cb) {
        if (!isInState(RoomSessionState.JOINED) || device == null || sendTransport == null
                || localMediaController == null) {
            Log.w(TAG, "Cannot produce video in state " + sessionState);
            cb.onError();
            return;
        }
        if (!device.canProduce(MediaKind.VIDEO.wireValue())) {
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

        VideoSink renderer = videoRendererDelegate.createRenderer("local", true);
        LocalVideoMedia videoMedia = localMediaController.createVideo(appContext, renderer);
        if (videoMedia == null) {
            Log.w(TAG, "Create video capturer failure.");
            cb.onError();
            return;
        }

        try {
            JSONObject codecOptions = RoomProtocol.videoCodecOptions(config.getVideoCodec());

            List<RtpParameters.Encoding> encodings = new ArrayList<>();
            SendTransport.SendResult sendResult = sendTransport.produce(videoMedia.getTrack(), encodings, codecOptions.toString(), null);
            JSONObject rtpParameters = new JSONObject(sendResult.rtpParameters);

            request("produce", new RoomProtocol.ProduceRequest(sendTransport.getId(), MediaKind.VIDEO, rtpParameters), new ResponseHandler() {
                @Override
                public void onSuccess(Response resp) {
                    try {

                        String id = RoomProtocol.ProduceResponse.fromJson(resp.getData()).id;

                        Producer producer = new Producer(id, sendResult.localId, sendResult.rtpSender,
                                videoMedia, rtpParameters, MediaKind.VIDEO.wireValue(), sendTransport);
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
        if (!isInState(RoomSessionState.JOINED) || device == null || sendTransport == null
                || localMediaController == null) {
            Log.w(TAG, "Cannot produce audio in state " + sessionState);
            cb.onError();
            return;
        }
        if (!device.canProduce(MediaKind.AUDIO.wireValue())) {
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

        LocalAudioMedia audioMedia = localMediaController.createAudio(muted);
        try {
            JSONObject codecOptions = RoomProtocol.audioCodecOptions(config.getAudioCodec());

            List<RtpParameters.Encoding> encodings = new ArrayList<>();
            SendTransport.SendResult sendResult = sendTransport.produce(audioMedia.getTrack(), encodings, codecOptions.toString(), null);
            JSONObject rtpParameters = new JSONObject(sendResult.rtpParameters);
            request("produce", new RoomProtocol.ProduceRequest(sendTransport.getId(), MediaKind.AUDIO, rtpParameters), new ResponseHandler() {
                @Override
                public void onSuccess(Response resp) {
                    try {
                        String id = RoomProtocol.ProduceResponse.fromJson(resp.getData()).id;
                        Producer producer = new Producer(id, sendResult.localId,
                                sendResult.rtpSender, audioMedia, rtpParameters,
                                MediaKind.AUDIO.wireValue(), sendTransport);
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
        if (!isInState(RoomSessionState.JOINED) || localMediaController == null) {
            return false;
        }
        Producer producer = findProducer(MediaKind.VIDEO);
        for (int i = 0; i < producers.size(); i++) {
            if (producers.get(i).kind.equals(MediaKind.VIDEO.wireValue())) {
                producer = producers.get(i);
                break;
            }
        }
        if (producer == null) {
            return false;
        }

        return localMediaController.switchCamera(producer.getLocalMedia());
    }

    public void applyMute(boolean muted) {
        if (!isInState(RoomSessionState.JOINED) || localMediaController == null) {
            return;
        }
        Producer producer = findProducer(MediaKind.AUDIO);
        if (producer != null) {
            localMediaController.setMuted(producer.getLocalMedia(), muted);
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
        if (!isInState(RoomSessionState.JOINED)) {
            return;
        }
        Producer audioProducer = findProducer(MediaKind.AUDIO);
        if (audioProducer != null) {
            audioProducer.close();
            closeProducer(audioProducer);
            producers.remove(audioProducer);
        }
    }

    public void closeVideoProducer() {
        if (!isInState(RoomSessionState.JOINED)) {
            return;
        }
        Producer videoProducer = findProducer(MediaKind.VIDEO);
        if (videoProducer != null) {
            videoProducer.close();
            closeProducer(videoProducer);
            producers.remove(videoProducer);
            videoRendererDelegate.removeRenderer("local");
        }
    }

    private Producer findProducer(MediaKind kind) {
        for (int i = 0; i < producers.size(); i++) {
            Producer p = producers.get(i);
            if (p.kind.equals(kind.wireValue())) {
                return p;
            }
        }
        return null;
    }


    void consumeProducer(String producerId, String peerId) {
        if (!isInState(RoomSessionState.JOINED) || recvTransport == null) {
            Log.w(TAG, "Ignoring producer consumption in state " + sessionState);
            return;
        }
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
                        if (consumer.kind.equals(MediaKind.VIDEO.wireValue())) {
                            VideoTrack track = (VideoTrack)consumer.getTrack();
                            VideoSink renderer = videoRendererDelegate.createRenderer(consumer.id, false);
                            track.addSink(renderer);
                        }
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
        device = new Device();
        device.load(routerRtpCaps, webRtcRuntime.getRtcConfiguration(),
                webRtcRuntime.getPeerConnectionFactory());
    }

    private void request(String method, ResponseHandler handler) {
        request(method, "{}", handler);
    }
    private void request(String method, JSONObject data, ResponseHandler handler) {
        request(method, data.toString(), handler);
    }

    private void request(String method, RoomProtocol.Payload data, ResponseHandler handler) throws JSONException {
        request(method, data.toJson(), handler);
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

}
