package com.beetle.conference;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.fragment.app.FragmentActivity;


/**
 * LoginActivity
 * Description: 登录页面,给用户指定消息发送方Id
 */
public class LoginActivity extends FragmentActivity {
    private final String TAG = "demo";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void enterRoom(View v) {
        EditText uidEditText = (EditText) findViewById(R.id.et_username);
        EditText conferenceEditText = (EditText) findViewById(R.id.conference_id);

        String uidText = uidEditText.getText().toString();
        String confText = conferenceEditText.getText().toString();

        if (TextUtils.isEmpty(uidText) || TextUtils.isEmpty(confText)) {
            return;
        }

        final long uid = Long.parseLong(uidText);
        final long conferenceID = Long.parseLong(confText);

        if (uid == 0 || conferenceID == 0) {
            return;
        }


        Log.i(TAG, "uid:" + uid + " channel id:" + conferenceID);

        Class cls = ConferenceActivity.class;
        Intent intent = new Intent(LoginActivity.this, cls);
        intent.putExtra("current_uid", uid);
        intent.putExtra("channel_id", "" + conferenceID);
        intent.putExtra("token", "1");

        startActivity(intent);
    }



}
