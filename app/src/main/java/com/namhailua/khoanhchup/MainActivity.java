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

    private TextView status;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(28, 35, 28, 35);

        TextView title = new TextView(this);
        title.setText("🐱 KHOANH CHỤP V7");
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        Button cat = new Button(this);
        cat.setText("BẬT MÈO TỰ ĐI");
        root.addView(cat);

        Button start = new Button(this);
        start.setText("BẮT ĐẦU PHIÊN CHỤP");
        root.addView(start);

        Button snap = new Button(this);
        snap.setText("KHOANH CHỤP NGAY");
        root.addView(snap);

        Button stop = new Button(this);
        stop.setText("DỪNG PHIÊN VÀ TẮT MÈO");
        root.addView(stop);

        status = new TextView(this);
        status.setText(
            "Bật mèo và bắt đầu phiên chụp."
        );
        status.setGravity(Gravity.CENTER);
        status.setTextSize(16);
        root.addView(status);

        setContentView(root);

        cat.setOnClickListener(v -> enableCat());

        start.setOnClickListener(v -> beginCapture());

        snap.setOnClickListener(v -> snapNow());

        stop.setOnClickListener(v -> {
            Intent captureService = new Intent(
                this, ScreenCaptureService.class
            );
            captureService.setAction(
                ScreenCaptureService.ACTION_STOP
            );
            startService(captureService);

            stopService(new Intent(
                this, FloatingCaptureService.class
            ));

            status.setText("Đã dừng phiên và tắt mèo.");
        });

        if (getIntent() != null &&
            "com.namhailua.khoanhchup.CAPTURE"
                .equals(getIntent().getAction())) {
            snapNow();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (intent != null &&
            "com.namhailua.khoanhchup.CAPTURE"
                .equals(intent.getAction())) {
            snapNow();
        }
    }

    private void enableCat() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(
                intent, REQUEST_OVERLAY
            );
            return;
        }

        try {
            Intent service = new Intent(
                this, FloatingCaptureService.class
            );

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(service);
            } else {
                startService(service);
            }

            status.setText("Mèo đang đi lại.");

        } catch (Exception e) {
            status.setText(
                "Lỗi bật mèo: " + e.getMessage()
            );
        }
    }

    private void beginCapture() {
        if (ScreenCaptureService.isActive()) {
            status.setText(
                "Phiên đang hoạt động. Chạm mèo để chụp."
            );
            moveTaskToBack(true);
            return;
        }

        try {
            MediaProjectionManager manager =
                (MediaProjectionManager)
                getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE
                );

            startActivityForResult(
                manager.createScreenCaptureIntent(),
                REQUEST_CAPTURE
            );

        } catch (Exception e) {
            status.setText(
                "Lỗi xin quyền: " + e.getMessage()
            );
        }
    }

    private void snapNow() {
        if (!ScreenCaptureService.isActive()) {
            beginCapture();
            return;
        }

        Intent service = new Intent(
            this, ScreenCaptureService.class
        );
        service.setAction(
            ScreenCaptureService.ACTION_SNAP
        );

        startService(service);
        moveTaskToBack(true);
    }

    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data
    ) {
        super.onActivityResult(
            requestCode, resultCode, data
        );

        if (requestCode == REQUEST_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                enableCat();
            } else {
                status.setText(
                    "Chưa cấp quyền mèo nổi."
                );
            }
            return;
        }

        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK &&
                data != null) {

                Intent service = new Intent(
                    this, ScreenCaptureService.class
                );
                service.setAction(
                    ScreenCaptureService.ACTION_START
                );
                service.putExtra(
                    "resultCode", resultCode
                );
                service.putExtra(
                    "resultData", data
                );

                try {
                    if (Build.VERSION.SDK_INT >= 26) {
                        startForegroundService(service);
                    } else {
                        startService(service);
                    }

                    status.setText(
                        "Đang bắt đầu phiên chụp."
                    );
                    moveTaskToBack(true);

                } catch (Exception e) {
                    status.setText(
                        "Lỗi: " + e.getMessage()
                    );
                }

            } else {
                status.setText("Đã hủy cấp quyền.");
            }
        }
    }
}
