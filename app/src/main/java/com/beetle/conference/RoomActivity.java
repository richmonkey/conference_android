package com.beetle.conference;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.beetle.room.ProduceCallback;
import com.beetle.room.Producer;
import com.beetle.room.RoomClient;
import com.beetle.room.RoomClientObserver;
import com.beetle.room.VideoRendererDelegate;
import org.webrtc.SurfaceViewRenderer;
import java.util.ArrayList;
import java.util.List;


abstract public class RoomActivity extends AppCompatActivity implements RoomClientObserver, VideoRendererDelegate {
    private static final String TAG = "RoomActivity";
    private static final long ACTIVE_SPEAKER_DETECTION_INTERVAL_MS = 50L;

    ArrayList<String> peers = new ArrayList<>();

    protected boolean cameraOn = true;
    protected boolean microphoneOn = true;

    protected boolean muted = false;

    protected RoomClient roomClient;

    private final Handler activeSpeakerHandler = new Handler(Looper.getMainLooper());
    private final Runnable activeSpeakerDetector = new Runnable() {
        @Override
        public void run() {
            if (roomClient == null) {
                return;
            }
            Log.i(TAG, "Active speaker peer IDs: "
                    + roomClient.detectActiveSpeakerPeerIds());
            activeSpeakerHandler.postDelayed(this, ACTIVE_SPEAKER_DETECTION_INTERVAL_MS);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        startActiveSpeakerDetection();
    }

    @Override
    protected void onPause() {
        stopActiveSpeakerDetection();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopActiveSpeakerDetection();
        super.onDestroy();

        peers.clear();

        if (roomClient != null) {
            roomClient.stop();
        }
    }

    private void startActiveSpeakerDetection() {
        if (roomClient == null) {
            return;
        }
        activeSpeakerHandler.removeCallbacks(activeSpeakerDetector);
        activeSpeakerHandler.post(activeSpeakerDetector);
    }

    private void stopActiveSpeakerDetection() {
        activeSpeakerHandler.removeCallbacks(activeSpeakerDetector);
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
    public void onError() {
        Log.e(TAG, "on room client error");
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
        roomClient.produceVideo(this.getApplicationContext(), new ProduceCallback() {
            @Override
            public void onSuccess(Producer producer) {

            }

            @Override
            public void onError() {
                Log.w(TAG, "produce video err");

            }
        });
    }

    void produceAudio() {
        roomClient.produceAudio(this.getApplicationContext(), muted, new ProduceCallback() {
            @Override
            public void onSuccess(Producer producer) {

            }

            @Override
            public void onError() {
                Log.w(TAG, "produce audio err");
            }
        });
    }
}
