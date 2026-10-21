package com.namhailua.khoanhchup;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.Toast;

public class FloatingCaptureService extends Service {

    private WindowManager windowManager;
    private TextView floatingButton;
    private static final String CHANNEL_ID = "khoanhchup_service";

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        Notification notification =
            new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Khoanh Chup HONOR")
                .setContentText("Nut chup noi dang hoat dong")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();

        startForeground(1, notification);

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this,
                "Hay cap quyen hien thi tren ung dung khac",
                Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        windowManager = (WindowManager)
            getSystemService(WINDOW_SERVICE);

        floatingButton = new TextView(this);
        floatingButton.setText("📸");
        floatingButton.setTextSize(25);
        floatingButton.setGravity(Gravity.CENTER);
        floatingButton.setTextColor(Color.WHITE);
        floatingButton.setTypeface(null, Typeface.BOLD);

        GradientDrawable background =
            new GradientDrawable();
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
            Toast.makeText(this,
                "Nut noi da hoat dong. Sap them chup man hinh!",
                Toast.LENGTH_SHORT).show();
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel =
                new NotificationChannel(
                    CHANNEL_ID,
                    "Khoanh Chup",
                    NotificationManager.IMPORTANCE_LOW
                );

            NotificationManager manager =
                getSystemService(NotificationManager.class);

            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (floatingButton != null &&
            windowManager != null) {
            windowManager.removeView(floatingButton);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
