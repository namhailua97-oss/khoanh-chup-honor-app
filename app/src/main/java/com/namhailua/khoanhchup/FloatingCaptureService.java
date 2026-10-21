package com.namhailua.khoanhchup;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingCaptureService extends Service {

    private WindowManager wm;
    private TextView cat;
    private WindowManager.LayoutParams params;

    private static final String CHANNEL = "khoanhchup_float";

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            startNotification();

            if (!Settings.canDrawOverlays(this)) {
                stopSelf();
                return;
            }

            createCatButton();

        } catch (Exception e) {
            Toast.makeText(this,
                "Loi: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void startNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel =
                new NotificationChannel(
                    CHANNEL,
                    "Khoanh Chup HONOR",
                    NotificationManager.IMPORTANCE_LOW
                );

            NotificationManager manager =
                getSystemService(NotificationManager.class);

            manager.createNotificationChannel(channel);
        }

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
            .setContentTitle("Khoanh Chup HONOR")
            .setContentText("Nut meo noi dang hoat dong")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build();

        startForeground(1001, notification);
    }

    private void createCatButton() {

        wm = (WindowManager)
            getSystemService(WINDOW_SERVICE);

        cat = new TextView(this);
        cat.setText("🐱");
        cat.setTextSize(35);
        cat.setGravity(Gravity.CENTER);
        cat.setBackgroundColor(Color.TRANSPARENT);
        cat.setIncludeFontPadding(false);

        int size = (int) (
            64 * getResources().getDisplayMetrics().density
        );

        params = new WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 20;
        params.y = 350;

        wm.addView(cat, params);

        final int touchSlop =
            ViewConfiguration.get(this).getScaledTouchSlop();

        cat.setOnTouchListener(new View.OnTouchListener() {

            private int startX;
            private int startY;
            private float downX;
            private float downY;
            private boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getActionMasked()) {

                    case MotionEvent.ACTION_DOWN:
                        startX = params.x;
                        startY = params.y;
                        downX = event.getRawX();
                        downY = event.getRawY();
                        dragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;

                        if (!dragging &&
                            Math.hypot(dx, dy) > touchSlop * 2) {
                            dragging = true;
                        }

                        if (dragging) {
                            params.x = startX + (int) dx;
                            params.y = startY + (int) dy;
                            wm.updateViewLayout(cat, params);
                        }

                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!dragging) {
                            Toast.makeText(
                                FloatingCaptureService.this,
                                "Da bam nut meo!",
                                Toast.LENGTH_SHORT
                            ).show();
                            v.performClick();
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        return true;
                }

                return false;
            }
        });
    }

    @Override
    public int onStartCommand(
        Intent intent, int flags, int startId
    ) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (wm != null && cat != null) {
            try {
                wm.removeView(cat);
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
