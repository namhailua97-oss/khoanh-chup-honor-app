package com.namhailua.khoanhchup;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.content.Context;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
        title.setText("🐱 KHOANH CHỤP V6");
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        Button cat = new Button(this);
        cat.setText("BẬT MÈO NỔI");
        root.addView(cat);

        Button capture = new Button(this);
        capture.setText("KHOANH CHỤP NGAY");
        root.addView(capture);

        Button stop = new Button(this);
        stop.setText("TẮT MÈO NỔI");
        root.addView(stop);

        status = new TextView(this);
        status.setText("Bật mèo nổi để bắt đầu.");
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        setContentView(root);

        cat.setOnClickListener(v -> enableCat());

        capture.setOnClickListener(v -> beginCapture());

        stop.setOnClickListener(v -> {
            stopService(new Intent(
                this, FloatingCaptureService.class
            ));
            status.setText("Đã tắt mèo nổi.");
        });

        if (getIntent() != null &&
            "com.namhailua.khoanhchup.CAPTURE"
                .equals(getIntent().getAction())) {
            beginCapture();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (intent != null &&
            "com.namhailua.khoanhchup.CAPTURE"
                .equals(intent.getAction())) {
            beginCapture();
        }
    }

    private void enableCat() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, REQUEST_OVERLAY);
            return;
        }

        try {
            Intent service = new Intent(
                this, FloatingCaptureService.class
            );
            startForegroundService(service);
            status.setText("Mèo nổi đã được bật.");
        } catch (Exception e) {
            status.setText("Lỗi bật mèo: " + e.getMessage());
        }
    }

    private void beginCapture() {
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

            status.setText("Đang xin quyền chụp màn hình.");

        } catch (Exception e) {
            status.setText(
                "Lỗi chụp: " + e.getMessage()
            );
        }
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
                    "Chưa cấp quyền hiển thị mèo nổi."
                );
            }
            return;
        }

        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent service = new Intent(
                    this, ScreenCaptureService.class
                );

                service.putExtra("resultCode", resultCode);
                service.putExtra("resultData", data);

                try {
                    startForegroundService(service);
                    moveTaskToBack(true);
                } catch (Exception e) {
                    status.setText(
                        "Không bắt đầu được chụp: "
                        + e.getMessage()
                    );
                }

            } else {
                status.setText("Đã hủy chụp màn hình.");
            }
        }
    }
}
