package com.namhailua.khoanhchup;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.content.Context;
import android.provider.Settings;
import android.net.Uri;
import android.widget.*;
import android.view.Gravity;

public class MainActivity extends Activity {

    private static final int REQUEST_CAPTURE = 101;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(30, 30, 30, 30);

        TextView title = new TextView(this);
        title.setText("🐱 KHOANH CHỤP HONOR V5");
        title.setTextSize(23);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        Button start = new Button(this);
        start.setText("BẬT MÈO KHOANH CHỤP");
        layout.addView(start);

        Button stop = new Button(this);
        stop.setText("DỪNG CHỤP");
        layout.addView(stop);

        status = new TextView(this);
        status.setText("Chạm BẬT để bắt đầu phiên chụp");
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 25, 0, 0);
        layout.addView(status);

        setContentView(layout);

        start.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                status.setText("Hãy cho phép hiển thị trên ứng dụng khác");
                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
                );
                startActivity(intent);
                return;
            }

            MediaProjectionManager manager =
                (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);

            startActivityForResult(
                manager.createScreenCaptureIntent(),
                REQUEST_CAPTURE
            );
        });

        stop.setOnClickListener(v -> {
            stopService(new Intent(this, FloatingCaptureService.class));
            status.setText("Đã dừng phiên chụp");
        });
    }

    @Override
    protected void onActivityResult(
        int requestCode, int resultCode, Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent service = new Intent(
                    this, FloatingCaptureService.class
                );

                service.putExtra("resultCode", resultCode);
                service.putExtra("resultData", data);

                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(service);
                } else {
                    startService(service);
                }

                status.setText("Đã yêu cầu bật mèo nổi 🐱");
            } else {
                status.setText("Bạn chưa cấp quyền chụp màn hình");
            }
        }
    }
}
