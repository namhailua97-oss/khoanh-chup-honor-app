package com.namhailua.khoanhchup;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;

public class MainActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(35, 70, 35, 30);
        layout.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("KHOANH CHUP HONOR");
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        Button enable = new Button(this);
        enable.setText("BAT NUT CAMERA NOI");
        layout.addView(enable);

        Button disable = new Button(this);
        disable.setText("TAT NUT CAMERA NOI");
        layout.addView(disable);

        status = new TextView(this);
        status.setText("San sang");
        status.setGravity(Gravity.CENTER);
        status.setTextSize(17);
        layout.addView(status);

        enable.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                status.setText("Hay cap quyen hien thi tren ung dung khac");

                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
                );
                startActivity(intent);
                return;
            }

            Intent service = new Intent(
                this, FloatingCaptureService.class
            );

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(service);
            } else {
                startService(service);
            }

            status.setText("Da bat nut camera noi");
        });

        disable.setOnClickListener(v -> {
            stopService(new Intent(
                this, FloatingCaptureService.class
            ));
            status.setText("Da tat nut camera noi");
        });
    }
}
