package com.namhailua.khoanhchup;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.Random;

public class FloatingCaptureService extends Service {

    private static final String CHANNEL = "cat_v8";
    private static final int CAT_W = 84;
    private static final int CAT_H = 94;

    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private CatView cat;

    private final Handler handler =
        new Handler(Looper.getMainLooper());

    private final Random random = new Random();

    private boolean destroyed = false;
    private boolean hidden = false;
    private boolean dragging = false;

    private float posX, posY;
    private float direction = -1;
    private float speed = 0;
    private float walkPhase = 0;

    private int screenW, screenH;
    private long nextAction = 0;
    private long actionEnd = 0;
    private long lastFrame = 0;

    // 0: đi, 1: nghỉ, 2: vẫy tay, 3: ngủ
    private int state = 0;

    private final Runnable animator = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;

            if (cat != null && !hidden) {
                updateAnimation();
                cat.invalidate();
            }

            handler.postDelayed(this, 33);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        wm = (WindowManager)
            getSystemService(WINDOW_SERVICE);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm =
                (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);

            nm.createNotificationChannel(
                new NotificationChannel(
                    CHANNEL,
                    "Mèo hoạt hình V8",
                    NotificationManager.IMPORTANCE_LOW
                )
            );
        }

        updateScreen();
    }

    @Override
    public int onStartCommand(
        Intent intent, int flags, int startId
    ) {
        String action =
            intent == null ? null : intent.getAction();

        if ("HIDE".equals(action)) {
            hidden = true;
            if (cat != null) {
                cat.setVisibility(View.GONE);
            }
            return START_STICKY;
        }

        if ("SHOW".equals(action) && cat != null) {
            hidden = false;
            cat.setVisibility(View.VISIBLE);
            lastFrame = SystemClock.uptimeMillis();
            return START_STICKY;
        }

        if (cat != null) return START_STICKY;

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            startCatForeground();
            createCat();
        } catch (Exception e) {
            Toast.makeText(
                this,
                "Lỗi mèo: " + e.getMessage(),
                Toast.LENGTH_LONG
            ).show();
            stopSelf();
        }

        return START_STICKY;
    }

    private void startCatForeground() {
        Intent open =
            new Intent(this, MainActivity.class);

        PendingIntent pi = PendingIntent.getActivity(
            this, 10, open,
            PendingIntent.FLAG_UPDATE_CURRENT |
            PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder =
            Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);

        Notification notification = builder
            .setSmallIcon(
                android.R.drawable.ic_menu_camera
            )
            .setContentTitle("Khoanh Chụp V8")
            .setContentText("Mèo đang đi và vẫy tay")
            .setContentIntent(pi)
            .setOngoing(true)
            .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                1001,
                notification,
                android.content.pm.ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(1001, notification);
        }
    }

    private float dp(float v) {
        return v *
            getResources().getDisplayMetrics().density;
    }

    private void updateScreen() {
        android.util.DisplayMetrics dm =
            new android.util.DisplayMetrics();

        wm.getDefaultDisplay().getRealMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
    }

    private float limit(float v, float max) {
        return Math.max(0, Math.min(max, v));
    }

    private void createCat() {
        cat = new CatView(this);

        params = new WindowManager.LayoutParams(
            (int) dp(CAT_W),
            (int) dp(CAT_H),
            WindowManager.LayoutParams
                .TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams
                .FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams
                .FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;

        posX = Math.max(0, screenW - dp(110));
        posY = dp(210);

        moveCat();

        cat.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            float startX, startY;
            boolean moved;

            @Override
            public boolean onTouch(
                View v, MotionEvent e
            ) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dragging = true;
                        moved = false;
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = posX;
                        startY = posY;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;

                        if (Math.abs(dx) > dp(8) ||
                            Math.abs(dy) > dp(8)) {
                            moved = true;
                        }

                        if (moved) {
                            posX = limit(
                                startX + dx,
                                screenW - dp(CAT_W)
                            );
                            posY = limit(
                                startY + dy,
                                screenH - dp(CAT_H + 35)
                            );
                            moveCat();
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        dragging = false;

                        if (!moved) {
                            openCapture();
                        } else {
                            chooseAction();
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        dragging = false;
                        return true;
                }
                return true;
            }
        });

        wm.addView(cat, params);

        chooseAction();
        handler.post(animator);
    }

    private void chooseAction() {
        long now = SystemClock.uptimeMillis();
        int choice = random.nextInt(10);

        if (choice < 6) {
            state = 0;
            direction = random.nextBoolean() ? 1 : -1;
            speed = dp(20 + random.nextInt(20));
            actionEnd = now + 2500
                + random.nextInt(3500);
        } else if (choice < 8) {
            state = 1;
            speed = 0;
            actionEnd = now + 1200
                + random.nextInt(1800);
        } else if (choice == 8) {
            state = 2;
            speed = 0;
            actionEnd = now + 2600;
        } else {
            state = 3;
            speed = 0;
            actionEnd = now + 3500;
        }

        nextAction = actionEnd;
    }

    private void updateAnimation() {
        if (dragging) return;

        long now = SystemClock.uptimeMillis();

        if (lastFrame == 0) lastFrame = now;

        float dt = Math.min(
            0.08f,
            (now - lastFrame) / 1000f
        );

        lastFrame = now;

        if (now >= nextAction) {
            chooseAction();
        }

        if (state == 0) {
            walkPhase += dt * 12f;
            posX += direction * speed * dt;

            float maxX =
                Math.max(0, screenW - dp(CAT_W));

            if (posX <= 0) {
                posX = 0;
                direction = 1;
            }

            if (posX >= maxX) {
                posX = maxX;
                direction = -1;
            }

            moveCat();
        } else if (state == 2) {
            walkPhase += dt * 8f;
        }
    }

    private void moveCat() {
        if (params == null) return;

        params.x = Math.round(posX);
        params.y = Math.round(posY);

        if (cat != null && cat.getParent() != null) {
            try {
                wm.updateViewLayout(cat, params);
            } catch (Exception ignored) {}
        }
    }

    private void openCapture() {
        if (ScreenCaptureService.isActive()) {
            Intent snap = new Intent(
                this, ScreenCaptureService.class
            );
            snap.setAction(
                ScreenCaptureService.ACTION_SNAP
            );

            try {
                startService(snap);
            } catch (Exception e) {
                Toast.makeText(
                    this,
                    "Không chụp được màn hình",
                    Toast.LENGTH_SHORT
                ).show();
            }
        } else {
            Intent open =
                new Intent(this, MainActivity.class);

            open.setAction(
                "com.namhailua.khoanhchup.CAPTURE"
            );
            open.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            );

            try {
                startActivity(open);
            } catch (Exception e) {
                Toast.makeText(
                    this,
                    "Mở ứng dụng để cấp quyền chụp",
                    Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    private class CatView extends View {

        private final Paint p =
            new Paint(Paint.ANTI_ALIAS_FLAG);

        private final int ORANGE =
            Color.rgb(244, 155, 75);

        private final int CREAM =
            Color.rgb(255, 243, 225);

        private final int DARK =
            Color.rgb(85, 57, 49);

        CatView(Context context) {
            super(context);
            setLayerType(
                View.LAYER_TYPE_SOFTWARE, null
            );
        }

        private void oval(
            Canvas c,
            float l, float t,
            float r, float b,
            int color
        ) {
            p.setColor(color);
            p.setStyle(Paint.Style.FILL);
            c.drawOval(l, t, r, b, p);
        }

        private void circle(
            Canvas c,
            float x, float y,
            float radius, int color
        ) {
            p.setColor(color);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(x, y, radius, p);
        }

        private void line(
            Canvas c,
            float x1, float y1,
            float x2, float y2,
            float width, int color
        ) {
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(width);
            p.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(x1, y1, x2, y2, p);
            p.setStyle(Paint.Style.FILL);
        }

        private void ear(
            Canvas c,
            float x, float y,
            boolean left
        ) {
            Path path = new Path();

            float sign = left ? -1 : 1;

            path.moveTo(x - 9, y + 12);
            path.lineTo(x + sign * 7, y - 12);
            path.lineTo(x + 12, y + 10);
            path.close();

            p.setColor(ORANGE);
            c.drawPath(path, p);

            circle(
                c, x + sign * 2, y + 5,
                4, Color.rgb(255, 174, 181)
            );
        }

        private void leg(
            Canvas c,
            float hipX,
            float hipY,
            float phase,
            boolean back
        ) {
            float swing =
                state == 0
                    ? (float) Math.sin(phase) * 8
                    : 0;

            float lift =
                state == 0
                    ? Math.max(
                        0,
                        (float) Math.cos(phase)
                    ) * 7
                    : 0;

            float footX = hipX + swing;
            float footY = 75 - lift;

            int color = back
                ? Color.rgb(209, 119, 62)
                : ORANGE;

            line(
                c,
                hipX, hipY,
                hipX + swing * .45f, 61,
                8, color
            );

            line(
                c,
                hipX + swing * .45f, 61,
                footX, footY,
                7, color
            );

            oval(
                c,
                footX - 5, footY - 3,
                footX + 7, footY + 3,
                CREAM
            );
        }

        @Override
        protected void onDraw(Canvas original) {
            super.onDraw(original);

            Canvas c = original;

            c.save();

            float sx = getWidth() / 84f;
            float sy = getHeight() / 94f;

            c.scale(sx, sy);

            // Quay hướng đi
            if (direction < 0) {
                c.scale(-1, 1, 42, 47);
            }

            float bob =
                state == 0
                    ? (float) Math.abs(
                        Math.sin(walkPhase)
                    ) * 2.5f
                    : 0;

            c.translate(0, -bob);

            // Bóng dưới chân
            oval(
                c, 15, 78, 75, 84,
                0x33000000
            );

            // Đuôi cong, đung đưa độc lập
            float tail =
                (float) Math.sin(
                    SystemClock.uptimeMillis() / 280.0
                ) * 8;

            p.setColor(ORANGE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(9);
            p.setStrokeCap(Paint.Cap.ROUND);

            Path tailPath = new Path();
            tailPath.moveTo(66, 48);
            tailPath.cubicTo(
                89, 39,
                74 + tail, 15,
                78 + tail, 22
            );

            c.drawPath(tailPath, p);
            p.setStyle(Paint.Style.FILL);

            // Hai chân phía sau
            leg(c, 54, 51, walkPhase + 3.14f, true);
            leg(c, 31, 51, walkPhase, true);

            // Thân mèo
            oval(
                c, 20, 34, 69, 66,
                ORANGE
            );

            oval(
                c, 24, 41, 62, 62,
                CREAM
            );

            // Chân phía trước
            leg(c, 29, 53, walkPhase + 3.14f, false);

            if (state == 2) {
                // Chân trước giơ lên vẫy
                float wave =
                    (float) Math.sin(walkPhase * 2) * 9;

                line(
                    c,
                    55, 49,
                    70 + wave, 26,
                    9, ORANGE
                );

                circle(
                    c,
                    70 + wave, 25,
                    6, CREAM
                );

                // Đệm chân
                circle(
                    c,
                    70 + wave, 25,
                    2.5f,
                    Color.rgb(255, 159, 167)
                );

            } else {
                leg(c, 55, 53, walkPhase, false);
            }

            // Tai
            ear(c, 28, 19, true);
            ear(c, 52, 19, false);

            // Đầu mèo
            circle(c, 41, 29, 23, ORANGE);

            // Má trắng
            oval(
                c, 24, 31, 58, 49,
                CREAM
            );

            // Vệt lông trên trán
            line(c, 35, 10, 36, 17, 2, DARK);
            line(c, 43, 9, 43, 16, 2, DARK);
            line(c, 51, 11, 49, 17, 2, DARK);

            boolean blink =
                (SystemClock.uptimeMillis() % 4100)
                    < 180;

            if (state == 3 || blink) {
                line(c, 30, 29, 37, 29, 2, DARK);
                line(c, 46, 29, 53, 29, 2, DARK);
            } else {
                oval(c, 30, 24, 38, 34, DARK);
                oval(c, 46, 24, 54, 34, DARK);

                circle(c, 33, 27, 2.3f, Color.WHITE);
                circle(c, 49, 27, 2.3f, Color.WHITE);
            }

            // Mũi và miệng
            circle(
                c, 42, 37, 3,
                Color.rgb(240, 120, 137)
            );

            line(c, 42, 39, 42, 42, 1.4f, DARK);
            line(c, 42, 42, 38, 44, 1.4f, DARK);
            line(c, 42, 42, 46, 44, 1.4f, DARK);

            // Râu
            line(c, 24, 39, 13, 36, 1.1f, DARK);
            line(c, 24, 43, 12, 44, 1.1f, DARK);
            line(c, 59, 39, 70, 36, 1.1f, DARK);
            line(c, 59, 43, 71, 44, 1.1f, DARK);

            // Má hồng
            circle(
                c, 27, 38, 3,
                0x77FF8899
            );
            circle(
                c, 56, 38, 3,
                0x77FF8899
            );

            if (state == 3) {
                p.setColor(
                    Color.rgb(123, 83, 176)
                );
                p.setTypeface(
                    Typeface.DEFAULT_BOLD
                );
                p.setTextSize(12);
                c.drawText("Zzz", 54, 9, p);
            }

            c.restore();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);

        if (cat != null) {
            try {
                wm.removeView(cat);
            } catch (Exception ignored) {}
            cat = null;
        }

        super.onDestroy();
    }
}
