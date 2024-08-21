package com.beetle.conference;
import android.util.Log;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoTrack;
import java.util.ArrayList;


abstract public class RoomActivity extends AppCompatActivity implements RoomClient.RoomClientObserver {
    private static final String TAG = "ConferenceActivity";


    ArrayList<RoomClient.Producer> producers = new ArrayList<>();
    ArrayList<String> peers = new ArrayList<>();
    ArrayList<RoomClient.Consumer> consumers = new ArrayList<>();

    protected boolean cameraOn = true;
    protected boolean microphoneOn = true;
    protected RoomClient roomClient;

    @Override
    protected void onDestroy() {
        super.onDestroy();

        consumers.clear();
        for (int i = 0; i < producers.size(); i++) {
            RoomClient.Producer producer = producers.get(i);
            producer.close();
        }
        producers.clear();
        peers.clear();

        if (roomClient != null) {
            roomClient.stop();
        }
    }

    public void switchCamera() {
        RoomClient.Producer producer = null;
        for (int i = 0; i < producers.size(); i++) {
            if (producers.get(i).kind.equals("video")) {
                producer = producers.get(i);
                break;
            }
        }
        if (producer == null) {
            return;
        }

        VideoCapturer videoCapturer = producer.getVideoCapturer();
        if (videoCapturer instanceof CameraVideoCapturer) {
            Log.d(TAG, "Switch camera");
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
            cameraVideoCapturer.switchCamera(null);
        } else {
            Log.d(TAG, "Will not switch camera, video caputurer is not a camera");
        }
    }


    public void toggleCamera() {
        RoomClient.Producer producer = null;
        for (int i = 0; i < producers.size(); i++) {
            if (producers.get(i).kind.equals("video")) {
                producer = producers.get(i);
                break;
            }
        }

        if (cameraOn) {
            if (producer == null) {
                //request produce ...
                return;
            }
        } else {
            if (producer != null) {
                //cameraOn mismatch producer
                cameraOn = true;
                return;
            }
        }
        if (producer != null) {
            roomClient.closeVideoProducer(producer);
            producers.remove(producer);
            removeRenderer("local");
            cameraOn = false;
        } else {
            produceVideo();
            cameraOn = true;
        }
    }

    public void toggleMic() {
        RoomClient.Producer producer = null;
        for (int i = 0; i < producers.size(); i++) {
            if (producers.get(i).kind.equals("audio")) {
                producer = producers.get(i);
                break;
            }
        }

        if (microphoneOn) {
            if (producer == null) {
                //request produce...
                return;
            }
        } else {
            if (producer != null) {
                //microphoneOn mismatch producer
                microphoneOn = true;
                return;
            }
        }

        if (producer != null) {
            roomClient.closeAudioProducer(producer);
            producers.remove(producer);
            microphoneOn = false;
        } else {
            produceAudio();
            microphoneOn = true;
        }
    }

    abstract public SurfaceViewRenderer createRenderer(String id, boolean isLocal);
    abstract public void removeRenderer(String id);

    @Override
    public void onConnect() {
        if (cameraOn) {
            produceVideo();
        }
        if (microphoneOn) {
            produceAudio();
        }
    }

    @Override
    public void onDisconnect() {
        for (int i = 0; i < consumers.size(); i++) {
            RoomClient.Consumer consumer = consumers.get(i);
            if (consumer.kind.equals("video")) {
                removeRenderer(consumer.id);
            }
        }
        consumers.clear();

        removeRenderer("local");
        for (int i = 0; i < producers.size(); i++) {
            RoomClient.Producer producer = producers.get(i);
            producer.close();
        }
        producers.clear();
        peers.clear();
    }

    @Override
    public void onClose() {

    }

    @Override
    public void onPeer(String peerId) {
        peers.add(peerId);
    }

    @Override
    public void onPeerClosed(String peerId) {
        peers.remove(peerId);
    }

    @Override
    public void onConsumer(RoomClient.Consumer consumer) {
        consumers.add(consumer);
        if (consumer.kind.equals("video")) {
            VideoTrack track = (VideoTrack)consumer.getTrack();
            SurfaceViewRenderer renderer = createRenderer(consumer.id, false);
            track.addSink(renderer);
        }
    }

    @Override
    public void onConsumerClosed(RoomClient.Consumer consumer) {
        int index = consumers.indexOf(consumer);
        if (index == -1) {
            return;
        }
        if (consumer.kind.equals("video")) {
            removeRenderer(consumer.id);
        }
        consumers.remove(index);
    }

    void produceVideo() {
        SurfaceViewRenderer renderer = createRenderer("local", true);
        roomClient.produceVideo(renderer, new RoomClient.ProduceCallback() {
            @Override
            public void onSuccess(RoomClient.Producer producer) {
                producers.add(producer);
            }

            @Override
            public void onError() {
                Log.w(TAG, "produce video err");
                removeRenderer("local");
            }
        });
    }

    void produceAudio() {
        roomClient.produceAudio(new RoomClient.ProduceCallback() {
            @Override
            public void onSuccess(RoomClient.Producer producer) {
                producers.add(producer);
            }

            @Override
            public void onError() {
                Log.w(TAG, "produce audio err");
            }
        });
    }
}

