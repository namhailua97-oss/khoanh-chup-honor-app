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
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingCaptureService extends Service {

    private WindowManager windowManager;
    private TextView floatingButton;
    private WindowManager.LayoutParams params;

    private static final String CHANNEL_ID = "khoanhchup_float";

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            startNotification();

            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this,
                    "Chua cap quyen nut noi",
                    Toast.LENGTH_LONG).show();
                stopSelf();
                return;
            }

            createFloatingButton();

        } catch (Exception e) {
            Toast.makeText(this,
                "Loi nut noi: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void startNotification() {

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel =
                new NotificationChannel(
                    CHANNEL_ID,
                    "Khoanh Chup HONOR",
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
            .setContentText("Nut camera noi dang bat")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build();

        startForeground(1001, notification);
    }

    private void createFloatingButton() {

        windowManager = (WindowManager)
            getSystemService(WINDOW_SERVICE);

        floatingButton = new TextView(this);
        floatingButton.setText("📷");
        floatingButton.setTextSize(25);
        floatingButton.setTextColor(Color.WHITE);
        floatingButton.setGravity(Gravity.CENTER);

        GradientDrawable background =
            new GradientDrawable();

        background.setColor(Color.rgb(35, 125, 255));
        background.setCornerRadius(100);

        floatingButton.setBackground(background);

        int type = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        int size = (int) (60 *
            getResources().getDisplayMetrics().density);

        params = new WindowManager.LayoutParams(
            size,
            size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 30;
        params.y = 350;

        windowManager.addView(floatingButton, params);

        floatingButton.setOnTouchListener(
            new android.view.View.OnTouchListener() {

                private int startX;
                private int startY;
                private float touchX;
                private float touchY;
                private boolean moved;

                @Override
                public boolean onTouch(
                    android.view.View view,
                    MotionEvent event
                ) {
                    switch (event.getAction()) {

                        case MotionEvent.ACTION_DOWN:
                            startX = params.x;
                            startY = params.y;
                            touchX = event.getRawX();
                            touchY = event.getRawY();
                            moved = false;
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            int dx = (int)
                                (event.getRawX() - touchX);
                            int dy = (int)
                                (event.getRawY() - touchY);

                            if (Math.abs(dx) > 10 ||
                                Math.abs(dy) > 10) {
                                moved = true;
                            }

                            params.x = startX + dx;
                            params.y = startY + dy;

                            windowManager.updateViewLayout(
                                floatingButton, params
                            );
                            return true;

                        case MotionEvent.ACTION_UP:
                            if (!moved) {
                                Toast.makeText(
                                    FloatingCaptureService.this,
                                    "Nut noi da hoat dong!",
                                    Toast.LENGTH_SHORT
                                ).show();
                            }
                            return true;
                    }

                    return false;
                }
            }
        );
    }

    @Override
    public int onStartCommand(
        Intent intent, int flags, int startId
    ) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (windowManager != null &&
            floatingButton != null) {
            try {
                windowManager.removeView(floatingButton);
            } catch (Exception ignored) {
            }
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
