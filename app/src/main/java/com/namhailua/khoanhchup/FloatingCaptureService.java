package com.namhailua.khoanhchup;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class FloatingCaptureService extends Service {

    private static final String CHANNEL = "cat_overlay_v6";

    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private CatImage cat;

    private final Handler handler =
        new Handler(Looper.getMainLooper());

    private long lastTouch;
    private boolean destroyed = false;

    private final Runnable animator = new Runnable() {
        @Override
        public void run() {
            if (destroyed || cat == null) return;
            cat.invalidate();
            handler.postDelayed(this, 80);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        wm = (WindowManager)
            getSystemService(WINDOW_SERVICE);

        NotificationManager nm =
            (NotificationManager)
            getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                new NotificationChannel(
                    CHANNEL,
                    "Mèo nổi Khoanh Chụp",
                    NotificationManager.IMPORTANCE_LOW
                )
            );
        }
    }

    @Override
    public int onStartCommand(
        Intent intent, int flags, int startId
    ) {

        if (intent != null) {
            String action = intent.getAction();

            if ("HIDE".equals(action)) {
                removeCat();
                return START_STICKY;
            }

            if ("SHOW".equals(action) && cat != null) {
                return START_STICKY;
            }
        }

        if (cat != null) return START_STICKY;

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

        Notification n =
            new Notification.Builder(this, CHANNEL)
                .setSmallIcon(
                    android.R.drawable.ic_menu_camera
                )
                .setContentTitle("Khoanh Chụp HONOR V6")
                .setContentText(
                    "Mèo đang nổi trên màn hình"
                )
                .setContentIntent(pi)
                .setOngoing(true)
                .build();

        try {

            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    1001,
                    n,
                    android.content.pm.ServiceInfo
                        .FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                );
            } else {
                startForeground(1001, n);
            }

            showCat();

        } catch (Exception e) {

            Toast.makeText(
                this,
                "Không mở được mèo: " + e.getMessage(),
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

    private void showCat() {

        if (cat != null) return;

        lastTouch = System.currentTimeMillis();

        cat = new CatImage(this);

        params = new WindowManager.LayoutParams(
            dp(106),
            dp(119),
            WindowManager.LayoutParams
                .TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams
                .FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams
                .FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;

        android.util.DisplayMetrics dm =
            new android.util.DisplayMetrics();

        wm.getDefaultDisplay().getRealMetrics(dm);

        params.x = Math.max(
            0, dm.widthPixels - dp(120)
        );

        params.y = dp(190);

        cat.setOnTouchListener(
            new View.OnTouchListener() {

                float downX, downY;
                int startX, startY;
                boolean moved;

                @Override
                public boolean onTouch(
                    View v, MotionEvent e
                ) {

                    switch (e.getActionMasked()) {

                        case MotionEvent.ACTION_DOWN:

                            downX = e.getRawX();
                            downY = e.getRawY();

                            startX = params.x;
                            startY = params.y;

                            moved = false;

                            lastTouch =
                                System.currentTimeMillis();

                            cat.tapTime = lastTouch;

                            return true;

                        case MotionEvent.ACTION_MOVE:

                            int dx = Math.round(
                                e.getRawX() - downX
                            );

                            int dy = Math.round(
                                e.getRawY() - downY
                            );

                            if (
                                Math.abs(dx) > dp(9) ||
                                Math.abs(dy) > dp(9)
                            ) {
                                moved = true;
                            }

                            if (moved) {

                                params.x = Math.max(
                                    0,
                                    Math.min(
                                        dm.widthPixels - dp(106),
                                        startX + dx
                                    )
                                );

                                params.y = Math.max(
                                    0,
                                    Math.min(
                                        dm.heightPixels - dp(125),
                                        startY + dy
                                    )
                                );

                                try {
                                    wm.updateViewLayout(
                                        cat, params
                                    );
                                } catch (Exception ignored) {}
                            }

                            lastTouch =
                                System.currentTimeMillis();

                            return true;

                        case MotionEvent.ACTION_UP:

                            lastTouch =
                                System.currentTimeMillis();

                            if (!moved) {
                                handler.postDelayed(
                                    () -> {
                                        if (!destroyed) {
                                            openCapture();
                                        }
                                    },
                                    180
                                );
                            }

                            return true;

                        case MotionEvent.ACTION_CANCEL:
                            return true;
                    }

                    return true;
                }
            }
        );

        wm.addView(cat, params);

        handler.removeCallbacks(animator);
        handler.post(animator);
    }

    private void openCapture() {

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
                "Không mở được chụp màn hình",
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void removeCat() {

        handler.removeCallbacks(animator);

        if (cat != null) {
            try {
                wm.removeView(cat);
            } catch (Exception ignored) {}

            cat = null;
        }
    }

    private class CatImage extends View {

        private final Paint paint = new Paint(
            Paint.ANTI_ALIAS_FLAG |
            Paint.FILTER_BITMAP_FLAG
        );

        private final Bitmap bitmap;

        long tapTime;

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

            boolean asleep =
                now - lastTouch >= 30000;

            float t = now / 1000f;

            float bounce = asleep
                ? 0
                : (float) Math.sin(t * 2.5f) * dp(2);

            float angle = asleep
                ? 72f
                : (float) Math.sin(t * 1.9f) * 4f;

            float size = asleep ? 0.76f : 1f;

            if (now - tapTime < 550) {
                angle = -12f;
                bounce = -dp(7);
            }

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;

            canvas.save();

            canvas.translate(cx, cy + bounce);
            canvas.rotate(angle);
            canvas.scale(size, size);

            paint.setColor(Color.WHITE);
            paint.setAlpha(asleep ? 225 : 255);

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

            if (asleep) {

                paint.setColor(
                    Color.rgb(115, 74, 181)
                );

                paint.setTypeface(
                    Typeface.DEFAULT_BOLD
                );

                paint.setTextSize(dp(17));
                paint.setAlpha(220);

                canvas.drawText(
                    "Zzz",
                    getWidth() * 0.55f,
                    dp(22),
                    paint
                );

                paint.setAlpha(255);
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

        removeCat();

        super.onDestroy();
    }
}
