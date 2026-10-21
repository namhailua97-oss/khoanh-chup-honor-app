package com.namhailua.khoanhchup;

import android.app.Activity;
import android.content.*;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 101;
    private static final int REQUEST_OVERLAY = 102;

    private boolean asking;
    private TextView status;

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(28, 35, 28, 35);

        TextView title = new TextView(this);
        title.setText("🐱 KHOANH CHỤP V6");
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        Button floatButton = new Button(this);
        floatButton.setText("BẬT MÈO NỔI");
        root.addView(floatButton);

        Button start = new Button(this);
        start.setText("BẮT ĐẦU PHIÊN CHỤP");
        root.addView(start);

        Button snap = new Button(this);
        snap.setText("KHOANH CHỤP NGAY");
        root.addView(snap);

        Button stop = new Button(this);
        stop.setText("DỪNG CHỤP VÀ TẮT MÈO");
        root.addView(stop);

        status = new TextView(this);
        status.setText("Bật mèo, rồi bắt đầu phiên chụp.");
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        setContentView(root);

        floatButton.setOnClickListener(v -> startFloating());

        start.setOnClickListener(v -> beginPermission());

        snap.setOnClickListener(v -> {
            if (ScreenCaptureService.isActive()) {
                startService(
                    new Intent(this, ScreenCaptureService.class)
                        .setAction(ScreenCaptureService.ACTION_SNAP)
                );
                moveTaskToBack(true);
            } else {
                beginPermission();
            }
        });

        stop.setOnClickListener(v -> {
            startService(
                new Intent(this, ScreenCaptureService.class)
                    .setAction(ScreenCaptureService.ACTION_STOP)
            );

            stopService(
                new Intent(this, FloatingCaptureService.class)
            );

            status.setText("Đã dừng");
        });

        handleIntent(getIntent());
    }

    private void startFloating() {
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(
                new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
                ),
                REQUEST_OVERLAY
            );
            return;
        }

        try {
            startForegroundService(
                new Intent(this, FloatingCaptureService.class)
            );
            status.setText("Mèo đã được bật");

        } catch (Exception e) {
            status.setText(
                "Không bật được mèo: " + e.getMessage()
            );
        }
    }

    private void handleIntent(Intent intent) {
        if (intent != null &&
            "com.namhailua.khoanhchup.CAPTURE"
                .equals(intent.getAction())) {

            if (ScreenCaptureService.isActive()) {
                startService(
                    new Intent(this, ScreenCaptureService.class)
                        .setAction(ScreenCaptureService.ACTION_SNAP)
                );
                moveTaskToBack(true);
            } else {
                beginPermission();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void beginPermission() {
        if (asking) return;

        if (!Settings.canDrawOverlays(this)) {
            startFloating();
            return;
        }

        if (ScreenCaptureService.isActive()) {
            status.setText("Phiên chụp đang hoạt động");
            return;
        }

        asking = true;

        MediaProjectionManager manager =
            (MediaProjectionManager)
                getSystemService(MEDIA_PROJECTION_SERVICE);

        startActivityForResult(
            manager.createScreenCaptureIntent(),
            REQUEST_CAPTURE
        );
    }

    @Override
    protected void onActivityResult(
        int request,
        int result,
        Intent data
    ) {
        super.onActivityResult(request, result, data);

        if (request == REQUEST_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                startFloating();
            }
            return;
        }

        if (request == REQUEST_CAPTURE) {
            asking = false;

            if (result != RESULT_OK || data == null) {
                status.setText("Bạn đã hủy cấp quyền");
                return;
            }

            Intent i = new Intent(
                this,
                ScreenCaptureService.class
            ).setAction(ScreenCaptureService.ACTION_START);

            i.putExtra("resultCode", result);
            i.putExtra("resultData", data);

            try {
                startForegroundService(i);
                moveTaskToBack(true);
            } catch (Exception e) {
                status.setText("Lỗi: " + e.getMessage());
            }
        }
    }
}
