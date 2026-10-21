package com.namhailua.khoanhchup;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.content.Context;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.provider.Settings;
import android.widget.*;
import android.view.Gravity;

public class MainActivity extends Activity {

    private static final int REQUEST_CAPTURE = 101;
    private static final int REQUEST_OVERLAY = 102;
    private TextView status;
    private boolean capturePending = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(35, 70, 35, 35);
        layout.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("KHOANH CHUP HONOR");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        Button start = new Button(this);
        start.setText("BAT NUT MEO NOI");
        layout.addView(start);

        Button capture = new Button(this);
        capture.setText("KHOANH CHUP NGAY");
        layout.addView(capture);

        Button stop = new Button(this);
        stop.setText("TAT NUT MEO NOI");
        layout.addView(stop);

        status = new TextView(this);
        status.setText("San sang");
        status.setGravity(Gravity.CENTER);
        status.setTextSize(16);
        layout.addView(status);

        setContentView(layout);

        start.setOnClickListener(v -> enableFloating());

        capture.setOnClickListener(v -> requestCapture());

        stop.setOnClickListener(v -> {
            stopService(new Intent(
                this, FloatingCaptureService.class));
            status.setText("Da tat nut noi");
        });

        handleIntent(getIntent());
    }

    private void enableFloating() {
        if (!Settings.canDrawOverlays(this)) {
            Intent permission = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivityForResult(permission, REQUEST_OVERLAY);
            return;
        }

        Intent service = new Intent(
            this, FloatingCaptureService.class);

        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(service);
            } else {
                startService(service);
            }
            status.setText("Da yeu cau bat nut meo");
        } catch (Exception e) {
            status.setText("Loi: " + e.getMessage());
        }
    }

    private void handleIntent(Intent intent) {
        if (intent != null &&
            "com.namhailua.khoanhchup.CAPTURE"
                .equals(intent.getAction())) {
            requestCapture();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void requestCapture() {
        if (capturePending) return;

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this,
                "Hay bat quyen hien thi tren ung dung khac",
                Toast.LENGTH_LONG).show();
            enableFloating();
            return;
        }

        capturePending = true;

        MediaProjectionManager manager =
            (MediaProjectionManager)
            getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        startActivityForResult(
            manager.createScreenCaptureIntent(),
            REQUEST_CAPTURE);
    }

    @Override
    protected void onActivityResult(
        int requestCode, int resultCode, Intent data) {

        super.onActivityResult(
            requestCode, resultCode, data);

        if (requestCode == REQUEST_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                enableFloating();
            }
            return;
        }

        if (requestCode == REQUEST_CAPTURE) {
            capturePending = false;

            if (resultCode != RESULT_OK || data == null) {
                status.setText("Da huy chup man hinh");
                return;
            }

            Intent service = new Intent(
                this, ScreenCaptureService.class);

            service.putExtra("resultCode", resultCode);
            service.putExtra("resultData", data);

            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(service);
                } else {
                    startService(service);
                }

                moveTaskToBack(true);
            } catch (Exception e) {
                status.setText("Loi chup: " + e.getMessage());
            }
        }
    }
}
