package com.namhailua.khoanhchup;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.view.*;
import android.widget.Toast;
import java.nio.ByteBuffer;
import java.io.OutputStream;

public class FloatingCaptureService extends Service {

    private WindowManager wm;
    private WindowManager.LayoutParams catParams;
    private View catView;
    private View cropView;
    private Handler handler = new Handler(Looper.getMainLooper());

    private MediaProjection projection;
    private android.hardware.display.VirtualDisplay display;
    private ImageReader reader;

    private int screenW, screenH, density;
    private boolean stopping = false;
    private boolean busy = false;
    private long lastTouch = System.currentTimeMillis();

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "khoanh_chup_v5";

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        android.util.DisplayMetrics dm =
            getResources().getDisplayMetrics();

        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        density = dm.densityDpi;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (projection != null) {
            return START_NOT_STICKY;
        }

        if (intent == null ||
            !intent.hasExtra("resultData")) {
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            createNotification();

            int resultCode =
                intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);

            Intent resultData;

            if (Build.VERSION.SDK_INT >= 33) {
                resultData = intent.getParcelableExtra(
                    "resultData", Intent.class
                );
            } else {
                resultData = intent.getParcelableExtra("resultData");
            }

            if (resultData == null) {
                stopSelf();
                return START_NOT_STICKY;
            }

            MediaProjectionManager manager =
                (MediaProjectionManager)
                getSystemService(MEDIA_PROJECTION_SERVICE);

            projection = manager.getMediaProjection(
                resultCode, resultData
            );

            if (projection == null) {
                stopSelf();
                return START_NOT_STICKY;
            }

            projection.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        handler.post(() -> stopSelf());
                    }
                },
                handler
            );

            reader = ImageReader.newInstance(
                screenW, screenH,
                android.graphics.PixelFormat.RGBA_8888,
                3
            );

            display = projection.createVirtualDisplay(
                "KhoanhChupV5",
                screenW,
                screenH,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(),
                null,
                handler
            );

            createCat();

        } catch (Exception e) {
            Toast.makeText(
                this,
                "Không bật được mèo: " + e.getMessage(),
                Toast.LENGTH_LONG
            ).show();
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void createNotification() {
        NotificationManager nm =
            (NotificationManager)
            getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel =
                new NotificationChannel(
                    CHANNEL_ID,
                    "Khoanh Chụp đang hoạt động",
                    NotificationManager.IMPORTANCE_LOW
                );
            nm.createNotificationChannel(channel);
        }

        Intent open = new Intent(this, MainActivity.class);

        PendingIntent pi = PendingIntent.getActivity(
            this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT |
            PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Khoanh Chụp HONOR V5")
            .setContentText("Mèo nổi đang hoạt động. Chạm để mở ứng dụng và dừng.")
            .setContentIntent(pi)
            .setOngoing(true)
            .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private int dp(float value) {
        return (int) (value *
            getResources().getDisplayMetrics().density + 0.5f);
    }

    private void createCat() {
        catView = new CatView(this);

        catParams = new WindowManager.LayoutParams(
            dp(90),
            dp(100),
            Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        );

        catParams.gravity = Gravity.TOP | Gravity.LEFT;
        catParams.x = screenW - dp(110);
        catParams.y = dp(190);

        wm.addView(catView, catParams);

        catView.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = catParams.x;
                        startY = catParams.y;
                        moved = false;
                        lastTouch = System.currentTimeMillis();
                        v.invalidate();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int)(event.getRawX() - downX);
                        int dy = (int)(event.getRawY() - downY);

                        if (Math.abs(dx) > dp(8) ||
                            Math.abs(dy) > dp(8)) {
                            moved = true;
                        }

                        if (moved) {
                            catParams.x = Math.max(
                                0,
                                Math.min(screenW - dp(90), startX + dx)
                            );
                            catParams.y = Math.max(
                                0,
                                Math.min(screenH - dp(120), startY + dy)
                            );
                            wm.updateViewLayout(catView, catParams);
                        }

                        lastTouch = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_UP:
                        lastTouch = System.currentTimeMillis();
                        v.invalidate();

                        if (!moved) {
                            captureScreen();
                        }
                        return true;
                }
                return true;
            }
        });
    }

    private class CatView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        long start = System.currentTimeMillis();

        CatView(Context c) {
            super(c);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void color(int c) {
            p.setColor(c);
            p.setStyle(Paint.Style.FILL);
            p.setStrokeWidth(2);
        }

        void oval(Canvas c, int col,
                  float l, float t, float r, float b) {
            color(col);
            c.drawOval(l, t, r, b, p);
        }

        void circle(Canvas c, int col,
                    float x, float y, float radius) {
            color(col);
            c.drawCircle(x, y, radius, p);
        }

        @Override
        protected void onDraw(Canvas original) {
            super.onDraw(original);

            Canvas c = original;
            int save = c.save();
            c.scale(getWidth()/100f, getHeight()/110f);

            long now = System.currentTimeMillis();
            boolean sleeping = now - lastTouch > 30000;
            float t = (now - start) / 1000f;
            float wag = (float)Math.sin(t * 3) * 9;

            int orange = Color.rgb(246, 165, 101);
            int cream = Color.rgb(255, 243, 229);
            int pink = Color.rgb(255, 172, 191);
            int purple = Color.rgb(108, 69, 161);

            // Đuôi chuyển động
            oval(c, orange, 66 + wag/2, 50,
                 95 + wag/2, 93);

            // Thân
            oval(c, cream, 24, 48, 77, 98);
            oval(c, orange, 30, 52, 42, 89);

            if (sleeping) {
                // Mèo nằm ngủ
                oval(c, cream, 8, 56, 88, 97);
                oval(c, orange, 16, 58, 48, 76);

                color(orange);
                Path ear1 = new Path();
                ear1.moveTo(20, 65);
                ear1.lineTo(23, 36);
                ear1.lineTo(43, 57);
                ear1.close();
                c.drawPath(ear1, p);

                Path ear2 = new Path();
                ear2.moveTo(57, 57);
                ear2.lineTo(76, 36);
                ear2.lineTo(79, 65);
                ear2.close();
                c.drawPath(ear2, p);

                oval(c, cream, 15, 48, 83, 87);

                // Mắt nhắm
                color(purple);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.5f);
                c.drawArc(29, 65, 42, 73,
                          0, 180, false, p);
                c.drawArc(56, 65, 69, 73,
                          0, 180, false, p);
                p.setStyle(Paint.Style.FILL);

                circle(c, pink, 25, 76, 5);
                circle(c, pink, 72, 76, 5);

                color(purple);
                p.setTextSize(13);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                c.drawText("Zzz", 66, 29, p);

            } else {
                // Tai mèo
                color(orange);
                Path left = new Path();
                left.moveTo(18, 37);
                left.lineTo(20, 7);
                left.lineTo(43, 27);
                left.close();
                c.drawPath(left, p);

                Path right = new Path();
                right.moveTo(58, 27);
                right.lineTo(82, 7);
                right.lineTo(84, 38);
                right.close();
                c.drawPath(right, p);

                color(pink);
                c.drawOval(25, 19, 36, 35, p);
                c.drawOval(65, 19, 76, 35, p);

                // Đầu
                oval(c, cream, 12, 24, 88, 80);
                oval(c, orange, 35, 23, 65, 42);

                // Chớp mắt
                boolean blink = ((long)(t * 10) % 45) < 2;

                if (blink) {
                    color(purple);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(3);
                    c.drawLine(27, 55, 42, 55, p);
                    c.drawLine(58, 55, 73, 55, p);
                    p.setStyle(Paint.Style.FILL);
                } else {
                    oval(c, purple, 27, 43, 43, 63);
                    oval(c, purple, 57, 43, 73, 63);
                    circle(c, Color.WHITE, 37, 48, 4);
                    circle(c, Color.WHITE, 67, 48, 4);
                }

                circle(c, pink, 24, 65, 6);
                circle(c, pink, 76, 65, 6);
                circle(c, pink, 50, 63, 3);

                color(purple);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.8f);
                c.drawArc(43, 62, 50, 70,
                          0, 170, false, p);
                c.drawArc(50, 62, 57, 70,
                          10, 170, false, p);
                p.setStyle(Paint.Style.FILL);

                oval(c, cream, 25, 83, 43, 102);
                oval(c, cream, 57, 83, 75, 102);
            }

            c.restoreToCount(save);

            postInvalidateDelayed(100);
        }
    }

    private void captureScreen() {
        if (busy || reader == null || cropView != null) {
            return;
        }

        busy = true;

        catView.setVisibility(View.INVISIBLE);

        handler.postDelayed(() -> {
            Bitmap bitmap = readScreen();

            if (bitmap == null) {
                catView.setVisibility(View.VISIBLE);
                busy = false;
                Toast.makeText(
                    this,
                    "Chưa lấy được ảnh. Hãy thử lại.",
                    Toast.LENGTH_SHORT
                ).show();
                return;
            }

            showCrop(bitmap);
        }, 400);
    }

    private Bitmap readScreen() {
        Image image = null;

        try {
            image = reader.acquireLatestImage();

            if (image == null) return null;

            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();

            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();

            int width = image.getWidth();
            int height = image.getHeight();

            int paddedWidth =
                width + (rowStride - pixelStride * width)
                / pixelStride;

            Bitmap full = Bitmap.createBitmap(
                paddedWidth, height,
                Bitmap.Config.ARGB_8888
            );

            full.copyPixelsFromBuffer(buffer);

            Bitmap cropped = Bitmap.createBitmap(
                full, 0, 0, width, height
            );

            full.recycle();
            return cropped;

        } catch (Exception e) {
            return null;
        } finally {
            if (image != null) image.close();
        }
    }

    private void showCrop(Bitmap bitmap) {
        CropOverlay overlay = new CropOverlay(this, bitmap);
        cropView = overlay;

        WindowManager.LayoutParams params =
            new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            );

        params.gravity = Gravity.TOP | Gravity.LEFT;

        try {
            wm.addView(cropView, params);
        } catch (Exception e) {
            cropView = null;
            catView.setVisibility(View.VISIBLE);
            busy = false;
            bitmap.recycle();
        }
    }

    private class CropOverlay extends View {
        Bitmap screenshot;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float x1, y1, x2, y2;
        boolean selected = false;

        CropOverlay(Context context, Bitmap b) {
            super(context);
            screenshot = b;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);

            c.drawBitmap(
                screenshot,
                null,
                new Rect(0, 0, getWidth(), getHeight()),
                p
            );

            p.setColor(0x55000000);
            c.drawRect(
                0, 0, getWidth(), getHeight(), p
            );

            if (selected) {
                p.setColor(Color.GREEN);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(3));

                c.drawRect(
                    Math.min(x1, x2),
                    Math.min(y1, y2),
                    Math.max(x1, x2),
                    Math.max(y1, y2),
                    p
                );

                p.setStyle(Paint.Style.FILL);
            }

            p.setColor(Color.WHITE);
            p.setTextSize(dp(17));
            p.setTypeface(Typeface.DEFAULT_BOLD);

            c.drawText(
                "Kéo tay để khoanh vùng",
                dp(16), dp(38), p
            );

            c.drawText(
                "LƯU",
                getWidth() - dp(70),
                getHeight() - dp(35),
                p
            );

            c.drawText(
                "HỦY",
                dp(20),
                getHeight() - dp(35),
                p
            );
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX();
            float y = e.getY();

            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (y > getHeight() - dp(85)) {
                        if (x < getWidth()/2f) {
                            closeCrop();
                        } else {
                            saveSelection();
                        }
                        return true;
                    }

                    x1 = x2 = x;
                    y1 = y2 = y;
                    selected = true;
                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (selected) {
                        x2 = x;
                        y2 = y;
                        invalidate();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (selected) {
                        x2 = x;
                        y2 = y;
                        invalidate();
                    }
                    return true;
            }

            return true;
        }

        void saveSelection() {
            if (!selected) {
                Toast.makeText(
                    FloatingCaptureService.this,
                    "Hãy khoanh vùng trước",
                    Toast.LENGTH_SHORT
                ).show();
                return;
            }

            float scaleX =
                (float)screenshot.getWidth()/getWidth();
            float scaleY =
                (float)screenshot.getHeight()/getHeight();

            int left = Math.max(
                0,
                (int)(Math.min(x1, x2)*scaleX)
            );

            int top = Math.max(
                0,
                (int)(Math.min(y1, y2)*scaleY)
            );

            int right = Math.min(
                screenshot.getWidth(),
                (int)(Math.max(x1, x2)*scaleX)
            );

            int bottom = Math.min(
                screenshot.getHeight(),
                (int)(Math.max(y1, y2)*scaleY)
            );

            if (right-left < 10 || bottom-top < 10) {
                Toast.makeText(
                    FloatingCaptureService.this,
                    "Vùng khoanh quá nhỏ",
                    Toast.LENGTH_SHORT
                ).show();
                return;
            }

            Bitmap result = Bitmap.createBitmap(
                screenshot,
                left, top,
                right-left, bottom-top
            );

            boolean ok = saveBitmap(result);
            result.recycle();

            Toast.makeText(
                FloatingCaptureService.this,
                ok ? "Đã lưu ảnh vào Thư viện"
                   : "Không lưu được ảnh",
                Toast.LENGTH_SHORT
            ).show();

            closeCrop();
        }

        void closeCrop() {
            try {
                wm.removeView(this);
            } catch (Exception ignored) {}

            cropView = null;

            if (!screenshot.isRecycled()) {
                screenshot.recycle();
            }

            if (catView != null) {
                catView.setVisibility(View.VISIBLE);
            }

            lastTouch = System.currentTimeMillis();
            busy = false;
        }
    }

    private boolean saveBitmap(Bitmap bitmap) {
        android.net.Uri uri = null;

        try {
            ContentValues values = new ContentValues();

            values.put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "KhoanhChup_" +
                System.currentTimeMillis() + ".png"
            );

            values.put(
                MediaStore.Images.Media.MIME_TYPE,
                "image/png"
            );

            if (Build.VERSION.SDK_INT >= 29) {
                values.put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/KhoanhChup"
                );
                values.put(
                    MediaStore.Images.Media.IS_PENDING, 1
                );
            }

            uri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            );

            if (uri == null) return false;

            try (OutputStream out =
                getContentResolver().openOutputStream(uri)) {

                if (out == null ||
                    !bitmap.compress(
                        Bitmap.CompressFormat.PNG, 100, out
                    )) {
                    throw new Exception("Không ghi được PNG");
                }
            }

            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues done = new ContentValues();
                done.put(
                    MediaStore.Images.Media.IS_PENDING, 0
                );
                getContentResolver().update(
                    uri, done, null, null
                );
            }

            return true;

        } catch (Exception e) {
            if (uri != null) {
                try {
                    getContentResolver().delete(
                        uri, null, null
                    );
                } catch (Exception ignored) {}
            }
            return false;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopping = true;

        if (cropView != null) {
            try {
                wm.removeView(cropView);
            } catch (Exception ignored) {}
            cropView = null;
        }

        if (catView != null) {
            try {
                wm.removeView(catView);
            } catch (Exception ignored) {}
            catView = null;
        }

        if (display != null) {
            display.release();
            display = null;
        }

        if (reader != null) {
            reader.close();
            reader = null;
        }

        if (projection != null) {
            MediaProjection p = projection;
            projection = null;
            p.stop();
        }

        super.onDestroy();
    }
}
