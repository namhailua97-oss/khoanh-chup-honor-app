package com.namhailua.khoanhchup;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingCaptureService extends Service {

    private WindowManager windowManager;
    private TextView floatingButton;
    private static final String CHANNEL_ID = "khoanhchup_service";

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Khoanh Chup",
                NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager manager =
                getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
            .setContentTitle("Khoanh Chup HONOR")
            .setContentText("Nut camera noi dang hoat dong")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build();

        startForeground(1, notification);

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        windowManager = (WindowManager)
            getSystemService(WINDOW_SERVICE);

        floatingButton = new TextView(this);
        floatingButton.setText("📸");
        floatingButton.setTextSize(26);
        floatingButton.setGravity(Gravity.CENTER);
        floatingButton.setTextColor(Color.WHITE);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(30, 130, 255));
        background.setCornerRadius(100);
        floatingButton.setBackground(background);

        int type = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params =
            new WindowManager.LayoutParams(
                150,
                150,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            );

        params.gravity = Gravity.TOP | Gravity.RIGHT;
        params.x = 10;
        params.y = 350;

        windowManager.addView(floatingButton, params);

        floatingButton.setOnClickListener(v -> {
            Toast.makeText(
                this,
                "Nut noi da hoat dong!",
                Toast.LENGTH_SHORT
            ).show();
        });
    }

    @Override
    public void onDestroy() {
        if (floatingButton != null && windowManager != null) {
            windowManager.removeView(floatingButton);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
