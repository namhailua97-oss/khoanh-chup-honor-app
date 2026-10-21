package com.namhailua.khoanhchup;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class FloatingCaptureService extends Service {

    private WindowManager wm;
    private TextView cat;
    private WindowManager.LayoutParams params;
    private static final String CHANNEL = "khoanh_float";

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            createNotification();

            if (!Settings.canDrawOverlays(this)) {
                stopSelf();
                return;
            }

            wm = (WindowManager)
                getSystemService(WINDOW_SERVICE);

            cat = new TextView(this);
            cat.setText("🐱");
            cat.setTextSize(36);
            cat.setGravity(Gravity.CENTER);
            cat.setBackgroundColor(0x00000000);
            cat.setIncludeFontPadding(false);

            int size = (int)(64 *
                getResources().getDisplayMetrics().density);

            params = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            );

            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.x = 25;
            params.y = 350;

            wm.addView(cat, params);

            int slop = ViewConfiguration.get(this)
                .getScaledTouchSlop() * 2;

            cat.setOnTouchListener(new View.OnTouchListener() {
                int startX, startY;
                float downX, downY;
                boolean dragging;

                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            startX = params.x;
                            startY = params.y;
                            downX = e.getRawX();
                            downY = e.getRawY();
                            dragging = false;
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float dx = e.getRawX() - downX;
                            float dy = e.getRawY() - downY;

                            if (Math.hypot(dx, dy) > slop) {
                                dragging = true;
                            }

                            if (dragging) {
                                params.x = startX + (int)dx;
                                params.y = startY + (int)dy;
                                wm.updateViewLayout(cat, params);
                            }
                            return true;

                        case MotionEvent.ACTION_UP:
                            if (!dragging) {
                                openCapture();
                            }
                            return true;
                    }
                    return true;
                }
            });

        } catch (Exception e) {
            Toast.makeText(this,
                "Loi nut noi: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void openCapture() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(
            "com.namhailua.khoanhchup.CAPTURE");
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK |
            Intent.FLAG_ACTIVITY_SINGLE_TOP |
            Intent.FLAG_ACTIVITY_CLEAR_TOP);

        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this,
                "Khong mo duoc chup man hinh",
                Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int onStartCommand(
        Intent intent, int flags, int startId) {

        if (cat != null && intent != null) {
            String action = intent.getAction();

            if ("HIDE".equals(action)) {
                cat.setVisibility(View.GONE);
            } else if ("SHOW".equals(action)) {
                cat.setVisibility(View.VISIBLE);
            }
        }

        return START_STICKY;
    }

    private void createNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel =
                new NotificationChannel(
                    CHANNEL,
                    "Khoanh Chup",
                    NotificationManager.IMPORTANCE_LOW);

            getSystemService(NotificationManager.class)
                .createNotificationChannel(channel);
        }

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Khoanh Chup HONOR")
            .setContentText("Nut meo dang hoat dong")
            .setOngoing(true)
            .build();

        startForeground(1001, notification);
    }

    @Override
    public void onDestroy() {
        if (wm != null && cat != null) {
            try {
                wm.removeView(cat);
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
