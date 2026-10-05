package com.beetle.conference;

import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import org.webrtc.SurfaceViewRenderer;
import java.util.ArrayList;
import java.util.List;


abstract public class RoomActivity extends AppCompatActivity implements RoomClient.RoomClientObserver, RoomClient.VideoRendererDelegate {
    private static final String TAG = "RoomActivity";

    ArrayList<String> peers = new ArrayList<>();

    protected boolean cameraOn = true;
    protected boolean microphoneOn = true;
    protected RoomClient roomClient;

    @Override
    protected void onDestroy() {
        super.onDestroy();

        peers.clear();

        if (roomClient != null) {
            roomClient.stop();
        }
    }

    public void switchCamera() {
        this.roomClient.switchCamera();
    }

    public void toggleCamera() {
        if (cameraOn) {
            this.roomClient.closeVideoProducer();
            cameraOn = false;
        } else {
            produceVideo();
            cameraOn = true;
        }
    }

    public void toggleMic() {
        if (microphoneOn) {
            roomClient.closeAudioProducer();
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

    }

    @Override
    public void onDisconnect() {
        peers.clear();
    }

    @Override
    public void onClose() {

    }

    @Override
    public void onJoined(List<String> peerIds) {
        peers.clear();
        peers.addAll(peerIds);

        if (cameraOn) {
            produceVideo();
        }
        if (microphoneOn) {
            produceAudio();
        }
    }

    @Override
    public void onPeer(String peerId) {
        peers.add(peerId);
    }

    @Override
    public void onPeerClosed(String peerId) {
        peers.remove(peerId);
    }


    void produceVideo() {
        roomClient.produceVideo(this.getApplicationContext(), new RoomClient.ProduceCallback() {
            @Override
            public void onSuccess(RoomClient.Producer producer) {

            }

            @Override
            public void onError() {
                Log.w(TAG, "produce video err");

            }
        });
    }

    void produceAudio() {
        roomClient.produceAudio(this.getApplicationContext(), new RoomClient.ProduceCallback() {
            @Override
            public void onSuccess(RoomClient.Producer producer) {

            }

            @Override
            public void onError() {
                Log.w(TAG, "produce audio err");
            }
        });
    }
}

