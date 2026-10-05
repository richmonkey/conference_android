package com.beetle.conference.room;

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
import org.mediasoup.MediaSoupClient;
import org.mediasoup.RecvTransport;
import org.mediasoup.SendTransport;
import org.mediasoup.Transport;
import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpParameters;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSink;

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

    WebRtcRuntime webRtcRuntime;
    LocalMediaController localMediaController;
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

        webRtcRuntime = new WebRtcRuntime(appContext);
        localMediaController = new LocalMediaController(webRtcRuntime);

        handler = new Handler(Looper.myLooper());
    }

    public EglBase getRootEglBase() {
        return webRtcRuntime.getRootEglBase();
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

        for (Producer producer : producers) {
            producer.close();
        }
        producers.clear();

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
        if (webRtcRuntime != null) {
            webRtcRuntime.release();
            webRtcRuntime = null;
            localMediaController = null;
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
        return webRtcRuntime.createRenderer(context, isLocal);
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

                        sendTransport = device.createSendTransport(id, iceParameters.toString(), iceCandidates.toString(), dtlsParameters.toString(),
                                webRtcRuntime.getRtcConfiguration(), webRtcRuntime.getPeerConnectionFactory());

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
                        recvTransport = device.createRecvTransport(id, iceParameters.toString(), iceCandidates.toString(), dtlsParameters.toString(),
                                webRtcRuntime.getRtcConfiguration(), webRtcRuntime.getPeerConnectionFactory());
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

        VideoSink renderer = videoRendererDelegate.createRenderer("local", true);
        LocalVideoMedia videoMedia = localMediaController.createVideo(appContext, renderer);
        if (videoMedia == null) {
            Log.w(TAG, "Create video capturer failure.");
            cb.onError();
            return;
        }

        try {
            JSONObject codecOptions = new JSONObject();
            codecOptions.put("videoGoogleStartBitrate", 1000);

            List<RtpParameters.Encoding> encodings = new ArrayList<>();
            SendTransport.SendResult sendResult = sendTransport.produce(videoMedia.getTrack(), encodings, codecOptions.toString(), null);
            JSONObject rtpParameters = new JSONObject(sendResult.rtpParameters);

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
                                videoMedia, rtpParameters, "video", sendTransport);
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

        LocalAudioMedia audioMedia = localMediaController.createAudio(muted);
        try {
            JSONObject codecOptions = new JSONObject();
            codecOptions.put("opusStereo", true);
            codecOptions.put("opusDtx", true);

            List<RtpParameters.Encoding> encodings = new ArrayList<>();
            SendTransport.SendResult sendResult = sendTransport.produce(audioMedia.getTrack(), encodings, codecOptions.toString(), null);
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
                                sendResult.rtpSender, audioMedia, rtpParameters,
                                "audio", sendTransport);
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

        return localMediaController.switchCamera(producer.getLocalMedia());
    }

    public void applyMute(boolean muted) {
        Producer producer = findProducer("audio");
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
