package com.beetle.conference;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import androidx.appcompat.app.AppCompatActivity;

import org.webrtc.CameraVideoCapturer;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;

import java.util.ArrayList;

public class ConferenceActivity extends AppCompatActivity implements RoomClient.GetVideoRenderer, RoomClient.ReleaseVideoRenderer {
    private static final String TAG = "ConferenceActivity";

    private static final int PERMISSIONS_REQUEST_CAMERA = 1;
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 2;
    private static final int PERMISSIONS_REQUEST_MEDIA = 3;

    public static long activityCount = 0;

    protected long currentUID;
    protected String channelID;
    protected String token;

    RoomClient roomClient;


    ArrayList<VideoRenderer> renderers = new ArrayList<>();
    private MusicIntentReceiver headsetReceiver;

    public static class VideoRenderer {
        public String id;
        public SurfaceViewRenderer renderer;
        public VideoRenderer(String id, SurfaceViewRenderer renderer) {
            this.id = id;
            this.renderer = renderer;
        }
    }

    private class MusicIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                int state = intent.getIntExtra("state", -1);
                switch (state) {
                    case 0:
                        Log.d(TAG, "Headset is unplugged");
                        audioManager.setSpeakerphoneOn(true);
                        break;
                    case 1:
                        Log.d(TAG, "Headset is plugged");
                        audioManager.setSpeakerphoneOn(false);
                        break;
                    default:
                        Log.d(TAG, "I have no idea what the headset state is");
                }
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        getWindow().addFlags( WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        super.onCreate(savedInstanceState);
        activityCount++;
        setContentView(R.layout.activity_conference);

        Intent intent = getIntent();
        currentUID = intent.getLongExtra("current_uid", 0);
        if (currentUID == 0) {
            Log.i(TAG, "current uid is 0");
            finish();
            return;
        }
        channelID = intent.getStringExtra("channel_id");
        if (TextUtils.isEmpty(channelID)) {
            Log.i(TAG, "channel id is empty");
            finish();
            return;
        }
        Log.i(TAG, "channel id:" + channelID);

        token = intent.getStringExtra("token");
        if (TextUtils.isEmpty(token)) {
            Log.i(TAG, "token is empty");
            finish();
            return;
        }

        headsetReceiver = new MusicIntentReceiver();
        roomClient = new RoomClient(getApplicationContext(), this, this, token, "" + currentUID);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int cameraPermission = (checkSelfPermission(Manifest.permission.CAMERA));
            int recordPermission = (checkSelfPermission(Manifest.permission.RECORD_AUDIO));

            if (cameraPermission != PackageManager.PERMISSION_GRANTED ||
                    recordPermission != PackageManager.PERMISSION_GRANTED) {
                requestPermission();
            } else {
                roomClient.start(getProtooUrl());
            }
        } else {
            roomClient.start(getProtooUrl());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(headsetReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(headsetReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        roomClient.stop();
        activityCount--;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_MEDIA ||
                requestCode == PERMISSIONS_REQUEST_CAMERA ||
                requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            for (int i = 0; i < grantResults.length; i++) {
                Log.i(TAG, "media permission:" +  permissions[i] + " result:" + grantResults[0]);
            }
            roomClient.start(getProtooUrl());
        }
    }

    String getProtooUrl() {
        //String protooUrl = "ws://192.168.1.101:4444/?peerId="+this.currentUID + "&roomId=" +this.channelID + "&mode=group";
        String protooUrl = "wss://jitsi.gobelieve.io/room?peerId=" + this.currentUID + "&roomId=" +this.channelID + "&mode=group";
        return protooUrl;
    }

    public void switchCamera(View view) {
        VideoCapturer videoCapturer = roomClient.getVideoCapturer();
        if (videoCapturer instanceof CameraVideoCapturer) {
            if (videoCapturer == null) {
                Log.e(TAG, "Failed to switch camera. ");
                return; // No video is sent or only one camera is available or error happened.
            }
            Log.d(TAG, "Switch camera");
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
            cameraVideoCapturer.switchCamera(null);
        } else {
            Log.d(TAG, "Will not switch camera, video caputurer is not a camera");
        }
    }
    public void onHangup(View view) {
        Log.i(TAG, "hangup");
        this.finish();
    }

    public void toggleCamera(View v) {
        boolean cameraOn = !roomClient.isCameraOn();
        if (cameraOn) {
            roomClient.produceVideo();
        } else {
            roomClient.closeVideoProducer();
        }
        roomClient.setCameraOn(cameraOn);
    }

    public void toggleMic(View view) {
        boolean microphoneOn = !roomClient.isMicrophoneOn();
        if (microphoneOn) {
            roomClient.produceAudio();
        } else {
            roomClient.closeAudioProducer();
        }
        roomClient.setMicrophoneOn(microphoneOn);
        ImageButton fab = findViewById(R.id.mute);
        if (microphoneOn) {
            fab.setImageDrawable(getResources().getDrawable(R.drawable.unmute, this.getTheme()));
        } else {
            fab.setImageDrawable(getResources().getDrawable(R.drawable.mute, this.getTheme()));
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int cameraPermission = (checkSelfPermission(Manifest.permission.CAMERA));
            int recordPermission = (checkSelfPermission(Manifest.permission.RECORD_AUDIO));

            if (cameraPermission != PackageManager.PERMISSION_GRANTED &&
                    recordPermission != PackageManager.PERMISSION_GRANTED) {
                try {
                    this.requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                            PERMISSIONS_REQUEST_MEDIA);
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            } else if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
                try {
                    this.requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            } else if (recordPermission != PackageManager.PERMISSION_GRANTED) {
                try {
                    this.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public SurfaceViewRenderer createRenderer(String id, boolean isLocal) {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int w = size.x/2;
        int h = w;
        int x = w*(renderers.size()%2);
        int y = h*(renderers.size()/2);

        SurfaceViewRenderer render = new org.webrtc.SurfaceViewRenderer(this);
        render.init(roomClient.getRootEglBase().getEglBaseContext(), null);
        if (isLocal) {
            render.setZOrderMediaOverlay(true);
            render.setMirror(true);//default front camera
            render.getHolder().setFormat(PixelFormat.TRANSPARENT);
        }

        RelativeLayout ll = (RelativeLayout) findViewById(R.id.relativeLayout);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        render.setLayoutParams(lp);
        ll.addView(render);

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchCamera(view);
            }
        };

        if (isLocal) {
            render.setOnClickListener(listener);
        }
        renderers.add(new VideoRenderer(id, render));

        return render;
    }

    public void removeRenderer(String id) {
        int index = -1;
        for (int i = 0; i < renderers.size(); i++) {
            if (renderers.get(i).id.equals(id)) {
                index = i;
                break;
            }
        }
        VideoRenderer r = renderers.get(index);
        renderers.remove(index);

        RelativeLayout ll = (RelativeLayout) findViewById(R.id.relativeLayout);
        ll.removeView(r.renderer);

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int w = size.x/2;
        int h = w;

        for (int i = index; i < renderers.size(); i++) {
            int x = w*(i%2);
            int y = h*(i/2);

            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(w, h);
            lp.leftMargin = x;
            lp.topMargin = y;
            renderers.get(i).renderer.setLayoutParams(lp);
        }
    }
}