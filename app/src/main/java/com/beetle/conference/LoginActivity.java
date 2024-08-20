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
    static boolean ENABLE_LOGIN = false;

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
        EditText uidEditText = (EditText)findViewById(R.id.et_username);
        EditText conferenceEditText = (EditText)findViewById(R.id.conference_id);

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

        final ProgressDialog dialog = ProgressDialog.show(this, null, "登录中...");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            String result = login(uid);
            handler.post(() -> {
                dialog.dismiss();

                if (TextUtils.isEmpty(result)) {
                    Toast.makeText(LoginActivity.this, "登陆失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                Log.i(TAG, "uid:" + uid + " channel id:" + conferenceID + " token:" + result);

                Class cls = ConferenceActivity.class;
                Intent intent = new Intent(LoginActivity.this, cls);
                intent.putExtra("current_uid", uid);
                intent.putExtra("channel_id", "" + conferenceID);
                intent.putExtra("token", result);

                startActivity(intent);
            });
        });
    }


    private String login(long uid) {
        if (!ENABLE_LOGIN) {
            return "null-token";
        }
        //调用app自身的登陆接口获取im服务必须的access token
        String URL = "https://demo.gobelieve.io";
        String uri = String.format("%s/auth/token", URL);

        try {
            java.net.URL url = new URL(uri);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-type", "application/json");
            connection.connect();

            JSONObject json = new JSONObject();
            json.put("uid", uid);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"));
            writer.write(json.toString());
            writer.close();

            int responseCode = connection.getResponseCode();
            if(responseCode != HttpURLConnection.HTTP_OK) {
                System.out.println("login failure code is:" + responseCode);
                return null;
            }

            InputStream inputStream = connection.getInputStream();

            //inputstream -> string
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            String str = result.toString(StandardCharsets.UTF_8.name());


            JSONObject jsonObject = new JSONObject(str);
            String accessToken = jsonObject.getString("token");
            return accessToken;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }


}
