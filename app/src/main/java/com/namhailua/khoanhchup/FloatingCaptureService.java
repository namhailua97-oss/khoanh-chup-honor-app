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

    private static final String CHANNEL = "cat_v9";
    private static final int CW = 82;
    private static final int CH = 100;

    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private CatView cat;

    private final Handler handler =
        new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private boolean destroyed = false;
    private boolean hidden = false;
    private boolean dragging = false;

    private float x, y;
    private float direction = 1;
    private float speed;
    private float phase;
    private float elapsed;

    private int screenW, screenH;
    private int state = 0;
    private long actionEnd;
    private long lastFrame;

    // 0 di, 1 ngoi, 2 vay tay, 3 ngu, 4 nhay
    private final Runnable animator = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;

            if (cat != null && !hidden) {
                update();
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
                    "Meo noi V9",
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
            if (cat != null)
                cat.setVisibility(View.GONE);
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
                "Loi meo: " + e.getMessage(),
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
            .setContentTitle("Khoanh Chup V9")
            .setContentText("Meo dang vui choi")
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

    private float clamp(float v, float max) {
        return Math.max(0, Math.min(max, v));
    }

    private void createCat() {
        cat = new CatView(this);

        params = new WindowManager.LayoutParams(
            (int) dp(CW),
            (int) dp(CH),
            WindowManager.LayoutParams
                .TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams
                .FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams
                .FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;

        x = Math.max(0, screenW - dp(110));
        y = dp(210);

        params.x = (int) x;
        params.y = (int) y;

        cat.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            float startX, startY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dragging = true;
                        moved = false;
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = x;
                        startY = y;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;

                        if (Math.abs(dx) > dp(8) ||
                            Math.abs(dy) > dp(8)) {
                            moved = true;
                        }

                        if (moved) {
                            x = clamp(
                                startX + dx,
                                screenW - dp(CW)
                            );
                            y = clamp(
                                startY + dy,
                                screenH - dp(CH + 35)
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
        lastFrame = SystemClock.uptimeMillis();
        handler.post(animator);
    }

    private void chooseAction() {
        long now = SystemClock.uptimeMillis();
        int n = random.nextInt(12);

        if (n < 6) {
            state = 0;
            direction = random.nextBoolean() ? 1 : -1;
            speed = dp(16 + random.nextInt(18));
            actionEnd = now + 2500
                + random.nextInt(3000);
        } else if (n < 8) {
            state = 1;
            speed = 0;
            actionEnd = now + 1800;
        } else if (n < 10) {
            state = 2;
            speed = 0;
            actionEnd = now + 2200;
        } else if (n == 10) {
            state = 3;
            speed = 0;
            actionEnd = now + 3500;
        } else {
            state = 4;
            speed = 0;
            actionEnd = now + 1600;
        }

        elapsed = 0;
    }

    private void update() {
        long now = SystemClock.uptimeMillis();

        float dt = Math.min(
            0.06f,
            (now - lastFrame) / 1000f
        );
        lastFrame = now;

        if (dragging) return;

        elapsed += dt;
        phase += dt * (state == 0 ? 11 : 5);

        if (now >= actionEnd)
            chooseAction();

        if (state == 0) {
            x += direction * speed * dt;

            float maxX =
                Math.max(0, screenW - dp(CW));

            if (x <= 0) {
                x = 0;
                direction = 1;
            }

            if (x >= maxX) {
                x = maxX;
                direction = -1;
            }

            moveCat();
        }
    }

    private void moveCat() {
        if (params == null) return;

        params.x = Math.round(x);
        params.y = Math.round(y);

        if (cat != null && cat.getParent() != null) {
            try {
                wm.updateViewLayout(cat, params);
            } catch (Exception ignored) {}
        }
    }

    private void openCapture() {
        try {
            Intent open =
                new Intent(this, MainActivity.class);

            open.setAction(
                "com.namhailua.khoanhchup.CAPTURE"
            );

            open.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            );

            startActivity(open);

        } catch (Exception e) {
            Toast.makeText(
                this,
                "Khong mo duoc khoanh chup",
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    private class CatView extends View {

        private final Paint p =
            new Paint(Paint.ANTI_ALIAS_FLAG);

        private final int ORANGE = 0xFFFFA44E;
        private final int LIGHT = 0xFFFFCA83;
        private final int CREAM = 0xFFFFF2DE;
        private final int DARK = 0xFF513B35;
        private final int PINK = 0xFFFFA6B4;

        CatView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        private void oval(
            Canvas c, float l, float t,
            float r, float b, int color
        ) {
            p.setShader(null);
            p.setColor(color);
            p.setStyle(Paint.Style.FILL);
            c.drawOval(l, t, r, b, p);
        }

        private void circle(
            Canvas c, float x, float y,
            float radius, int color
        ) {
            p.setShader(null);
            p.setColor(color);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(x, y, radius, p);
        }

        private void glossyOval(
            Canvas c, float l, float t,
            float r, float b,
            int topColor, int bottomColor
        ) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(
                l, t, r, b,
                topColor, bottomColor,
                Shader.TileMode.CLAMP
            ));
            c.drawOval(l, t, r, b, p);
            p.setShader(null);
        }

        private void line(
            Canvas c,
            float x1, float y1,
            float x2, float y2,
            float width, int color
        ) {
            p.setShader(null);
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(width);
            p.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(x1, y1, x2, y2, p);
            p.setStyle(Paint.Style.FILL);
        }

        private void leg(
            Canvas c,
            float hipX, float hipY,
            float offset, boolean back
        ) {
            float swing = state == 0
                ? (float) Math.sin(phase + offset) * 7
                : 0;

            float lift = state == 0
                ? Math.max(
                    0,
                    (float) Math.cos(phase + offset)
                ) * 6
                : 0;

            float fx = hipX + swing;
            float fy = 78 - lift;

            int color = back ? 0xFFD98A4A : ORANGE;

            line(
                c, hipX, hipY,
                hipX + swing * .5f, 66,
                10, color
            );

            line(
                c,
                hipX + swing * .5f, 66,
                fx, fy,
                8, color
            );

            glossyOval(
                c,
                fx - 6, fy - 4,
                fx + 7, fy + 3,
                CREAM, 0xFFE5D4C2
            );
        }

        private void ear(
            Canvas c, float x,
            float y, boolean left
        ) {
            Path path = new Path();

            if (left) {
                path.moveTo(x - 13, y + 11);
                path.lineTo(x - 9, y - 14);
                path.lineTo(x + 11, y + 6);
            } else {
                path.moveTo(x - 11, y + 6);
                path.lineTo(x + 9, y - 14);
                path.lineTo(x + 13, y + 11);
            }

            path.close();

            p.setColor(ORANGE);
            p.setStyle(Paint.Style.FILL);
            c.drawPath(path, p);

            oval(
                c, x - 5, y - 3,
                x + 5, y + 9, PINK
            );
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);

            c.save();
            c.scale(
                getWidth() / 82f,
                getHeight() / 100f
            );

            if (direction < 0)
                c.scale(-1, 1, 41, 50);

            float bob = state == 0
                ? (float) Math.abs(
                    Math.sin(phase)
                ) * 2.5f
                : 0;

            float jump = state == 4
                ? (float) Math.abs(
                    Math.sin(elapsed * 7)
                ) * 13
                : 0;

            oval(
                c, 12, 82,
                73, 89, 0x33000000
            );

            c.translate(0, -bob - jump);

            float tail =
                (float) Math.sin(
                    SystemClock.uptimeMillis() / 260.0
                ) * 8;

            Path tailPath = new Path();
            tailPath.moveTo(65, 51);
            tailPath.cubicTo(
                88, 43,
                72 + tail, 12,
                76 + tail, 20
            );

            p.setColor(ORANGE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(10);
            p.setStrokeCap(Paint.Cap.ROUND);
            c.drawPath(tailPath, p);
            p.setStyle(Paint.Style.FILL);

            leg(c, 55, 53, 3.14f, true);
            leg(c, 30, 53, 0, true);

            glossyOval(
                c, 18, 35, 69, 68,
                LIGHT, 0xFFD9823F
            );

            glossyOval(
                c, 25, 43, 61, 65,
                CREAM, 0xFFE8D9C6
            );

            leg(c, 29, 54, 3.14f, false);

            if (state == 2) {
                float wave =
                    (float) Math.sin(phase * 2) * 8;

                line(
                    c, 55, 51,
                    69 + wave, 28,
                    10, ORANGE
                );

                circle(
                    c, 69 + wave, 27,
                    7, CREAM
                );

                circle(
                    c, 69 + wave, 27,
                    3, PINK
                );
            } else {
                leg(c, 55, 54, 0, false);
            }

            ear(c, 27, 20, true);
            ear(c, 54, 20, false);

            glossyOval(
                c, 17, 9, 65, 57,
                LIGHT, 0xFFE99549
            );

            glossyOval(
                c, 23, 30, 59, 51,
                CREAM, 0xFFF1E0CB
            );

            line(c, 35, 13, 36, 19, 2, DARK);
            line(c, 43, 12, 43, 19, 2, DARK);
            line(c, 51, 14, 49, 20, 2, DARK);

            boolean blink =
                SystemClock.uptimeMillis() % 3900 < 150;

            if (state == 3 || blink) {
                line(c, 28, 31, 37, 31, 2, DARK);
                line(c, 46, 31, 55, 31, 2, DARK);
            } else {
                glossyOval(
                    c, 27, 25, 39, 39,
                    0xFF66483B, 0xFF201D22
                );

                glossyOval(
                    c, 44, 25, 56, 39,
                    0xFF66483B, 0xFF201D22
                );

                circle(c, 32, 29, 3, Color.WHITE);
                circle(c, 49, 29, 3, Color.WHITE);
                circle(c, 35, 34, 1.2f, Color.WHITE);
                circle(c, 52, 34, 1.2f, Color.WHITE);
            }

            circle(c, 41, 39, 3.3f, PINK);

            line(c, 41, 41, 41, 44, 1.5f, DARK);
            line(c, 41, 44, 37, 46, 1.5f, DARK);
            line(c, 41, 44, 45, 46, 1.5f, DARK);

            line(c, 24, 40, 12, 37, 1, DARK);
            line(c, 24, 44, 12, 45, 1, DARK);
            line(c, 58, 40, 70, 37, 1, DARK);
            line(c, 58, 44, 70, 45, 1, DARK);

            circle(c, 26, 39, 3, 0x77FF8299);
            circle(c, 57, 39, 3, 0x77FF8299);

            if (state == 3) {
                p.setShader(null);
                p.setColor(0xFF9566CC);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextSize(12);
                c.drawText("Zzz", 54, 10, p);
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
