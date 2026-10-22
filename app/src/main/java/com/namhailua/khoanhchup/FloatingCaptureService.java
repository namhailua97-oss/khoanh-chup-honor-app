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

    private static final String CHANNEL = "cat_overlay_v7";

    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private CatImage cat;

    private final Handler handler =
        new Handler(Looper.getMainLooper());

    private final Random random = new Random();

    private boolean destroyed = false;
    private boolean dragging = false;
    private boolean hidden = false;

    private long nextChange;
    private long sleepUntil;

    private float speedX;
    private float speedY;
    private float posX;
    private float posY;

    private int screenWidth;
    private int screenHeight;

    private static final int CAT_WIDTH = 78;
    private static final int CAT_HEIGHT = 88;

    private final Runnable animator = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;

            if (cat != null && !hidden) {
                updateCat();
                cat.invalidate();
            }

            handler.postDelayed(this, 40);
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
                    "Mèo nổi V7",
                    NotificationManager.IMPORTANCE_LOW
                )
            );
        }

        updateScreenSize();
    }

    @Override
    public int onStartCommand(
        Intent intent, int flags, int startId
    ) {
        String action =
            intent == null ? null : intent.getAction();

        if ("HIDE".equals(action)) {
            hideCat();
            return START_STICKY;
        }

        if ("SHOW".equals(action) && cat != null) {
            hidden = false;
            cat.setVisibility(View.VISIBLE);
            chooseMovement();
            return START_STICKY;
        }

        if (cat != null) {
            return START_STICKY;
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        Intent open =
            new Intent(this, MainActivity.class);

        PendingIntent pi = PendingIntent.getActivity(
            this,
            10,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT |
            PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(
                this, CHANNEL
            );
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
            .setSmallIcon(
                android.R.drawable.ic_menu_camera
            )
            .setContentTitle("Khoanh Chụp V7")
            .setContentText("Mèo đang đi lại")
            .setContentIntent(pi)
            .setOngoing(true)
            .build();

        try {
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

    private int dp(float value) {
        return (int) (
            value *
            getResources().getDisplayMetrics().density
            + 0.5f
        );
    }

    private void updateScreenSize() {
        android.util.DisplayMetrics dm =
            new android.util.DisplayMetrics();

        wm.getDefaultDisplay().getRealMetrics(dm);

        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
    }

    private int maxX() {
        return Math.max(
            0, screenWidth - dp(CAT_WIDTH)
        );
    }

    private int maxY() {
        return Math.max(
            0, screenHeight - dp(CAT_HEIGHT)
                - dp(35)
        );
    }

    private float clamp(
        float value, float min, float max
    ) {
        return Math.max(
            min, Math.min(max, value)
        );
    }

    private void createCat() {
        if (cat != null) return;

        hidden = false;
        cat = new CatImage(this);

        params = new WindowManager.LayoutParams(
            dp(CAT_WIDTH),
            dp(CAT_HEIGHT),
            WindowManager.LayoutParams
                .TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams
                .FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams
                .FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;

        posX = maxX() - dp(12);
        posY = dp(190);

        params.x = Math.round(
            clamp(posX, 0, maxX())
        );
        params.y = Math.round(
            clamp(posY, 0, maxY())
        );

        posX = params.x;
        posY = params.y;

        cat.setOnTouchListener(
            new View.OnTouchListener() {

                float downX, downY;
                int startX, startY;
                boolean moved;

                @Override
                public boolean onTouch(
                    View v, MotionEvent event
                ) {
                    switch (event.getActionMasked()) {

                        case MotionEvent.ACTION_DOWN:
                            dragging = true;
                            moved = false;

                            downX = event.getRawX();
                            downY = event.getRawY();

                            startX = params.x;
                            startY = params.y;

                            cat.tapTime =
                                System.currentTimeMillis();

                            return true;

                        case MotionEvent.ACTION_MOVE:
                            int dx = Math.round(
                                event.getRawX() - downX
                            );

                            int dy = Math.round(
                                event.getRawY() - downY
                            );

                            if (
                                Math.abs(dx) > dp(8) ||
                                Math.abs(dy) > dp(8)
                            ) {
                                moved = true;
                            }

                            if (moved) {
                                posX = clamp(
                                    startX + dx, 0, maxX()
                                );

                                posY = clamp(
                                    startY + dy, 0, maxY()
                                );

                                moveView();
                            }

                            return true;

                        case MotionEvent.ACTION_UP:
                            dragging = false;

                            if (!moved) {
                                cat.tapTime =
                                    System.currentTimeMillis();

                                handler.postDelayed(
                                    () -> {
                                        if (!destroyed) {
                                            openCapture();
                                        }
                                    },
                                    180
                                );
                            } else {
                                chooseMovement();
                            }

                            return true;

                        case MotionEvent.ACTION_CANCEL:
                            dragging = false;
                            return true;
                    }

                    return true;
                }
            }
        );

        wm.addView(cat, params);

        chooseMovement();

        handler.removeCallbacks(animator);
        handler.post(animator);
    }

    private void chooseMovement() {
        long now = System.currentTimeMillis();

        int choice = random.nextInt(6);

        if (choice == 0) {
            speedX = 0;
            speedY = 0;

            sleepUntil = now + 2500
                + random.nextInt(4500);

        } else {
            float density =
                getResources()
                    .getDisplayMetrics().density;

            speedX =
                (random.nextBoolean() ? 1 : -1)
                * (0.7f +
                    random.nextFloat() * 1.1f)
                * density;

            speedY =
                (random.nextFloat() - 0.5f)
                * 0.6f
                * density;

            sleepUntil = 0;
        }

        nextChange = now + 1800
            + random.nextInt(3000);
    }

    private void updateCat() {
        if (dragging || cat == null) return;

        long now = System.currentTimeMillis();

        if (now >= nextChange) {
            chooseMovement();
        }

        if (now < sleepUntil) return;

        posX += speedX;
        posY += speedY;

        if (posX <= 0) {
            posX = 0;
            speedX = Math.abs(speedX);
        }

        if (posX >= maxX()) {
            posX = maxX();
            speedX = -Math.abs(speedX);
        }

        if (posY <= dp(20)) {
            posY = dp(20);
            speedY = Math.abs(speedY);
        }

        if (posY >= maxY()) {
            posY = maxY();
            speedY = -Math.abs(speedY);
        }

        cat.facingLeft = speedX < 0;

        moveView();
    }

    private void moveView() {
        if (cat == null || params == null) return;

        params.x = Math.round(posX);
        params.y = Math.round(posY);

        try {
            wm.updateViewLayout(cat, params);
        } catch (Exception ignored) {}
    }

    private void hideCat() {
        hidden = true;

        if (cat != null) {
            cat.setVisibility(View.GONE);
        }
    }

    private void openCapture() {
        if (destroyed) return;

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
                    "Lỗi chụp: " + e.getMessage(),
                    Toast.LENGTH_SHORT
                ).show();
            }

        } else {

            Intent open = new Intent(
                this, MainActivity.class
            );

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
                    "Hãy mở ứng dụng để cấp quyền chụp",
                    Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    private class CatImage extends View {

        private final Paint paint = new Paint(
            Paint.ANTI_ALIAS_FLAG |
            Paint.FILTER_BITMAP_FLAG
        );

        private final Bitmap bitmap;

        boolean facingLeft = false;
        long tapTime = 0;

        CatImage(Context context) {
            super(context);

            bitmap = BitmapFactory.decodeResource(
                getResources(),
                R.drawable.cat_overlay
            );

            setLayerType(
                View.LAYER_TYPE_SOFTWARE,
                null
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (bitmap == null) return;

            long now = System.currentTimeMillis();

            boolean sleeping =
                now < sleepUntil;

            float t = now / 1000f;

            float bounce = sleeping
                ? 0
                : (float) Math.sin(t * 9f) * dp(2);

            float angle = sleeping
                ? 15f
                : (float) Math.sin(t * 5f) * 5f;

            if (now - tapTime < 500) {
                bounce = -dp(6);
                angle = -12f;
            }

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;

            canvas.save();

            canvas.translate(cx, cy + bounce);

            if (facingLeft) {
                canvas.scale(-1f, 1f);
            }

            canvas.rotate(angle);

            paint.setColor(Color.WHITE);
            paint.setAlpha(
                sleeping ? 220 : 255
            );

            canvas.drawBitmap(
                bitmap,
                null,
                new RectF(
                    -getWidth() * 0.48f,
                    -getHeight() * 0.48f,
                    getWidth() * 0.48f,
                    getHeight() * 0.48f
                ),
                paint
            );

            canvas.restore();

            if (sleeping) {
                paint.setColor(
                    Color.rgb(115, 74, 181)
                );

                paint.setTypeface(
                    Typeface.DEFAULT_BOLD
                );

                paint.setTextSize(dp(13));

                canvas.drawText(
                    "Zzz",
                    getWidth() * 0.53f,
                    dp(15),
                    paint
                );
            }
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
