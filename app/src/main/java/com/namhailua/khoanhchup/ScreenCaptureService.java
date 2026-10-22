package com.namhailua.khoanhchup;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {

    public static final String ACTION_START =
        "com.namhailua.khoanhchup.START";
    public static final String ACTION_SNAP =
        "com.namhailua.khoanhchup.SNAP";
    public static final String ACTION_STOP =
        "com.namhailua.khoanhchup.STOP";

    private static final String CHANNEL = "capture_v7";
    private static volatile boolean active = false;

    public static boolean isActive() {
        return active;
    }

    private final Handler handler =
        new Handler(Looper.getMainLooper());

    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private WindowManager wm;
    private View overlay;
    private Bitmap screenshot;

    private boolean pending = false;
    private boolean destroyed = false;
    private long captureAfter = 0;

    private int width;
    private int height;

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
                    "Phiên khoanh chụp V7",
                    NotificationManager.IMPORTANCE_LOW
                )
            );
        }
    }

    @Override
    public int onStartCommand(
        Intent intent, int flags, int startId
    ) {
        if (intent == null) {
            if (!active) stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_SNAP.equals(action)) {
            if (active) requestSnapshot();
            return START_STICKY;
        }

        if (active) return START_STICKY;

        int resultCode = intent.getIntExtra(
            "resultCode", Activity.RESULT_CANCELED
        );

        Intent data;

        if (Build.VERSION.SDK_INT >= 33) {
            data = intent.getParcelableExtra(
                "resultData", Intent.class
            );
        } else {
            data = intent.getParcelableExtra("resultData");
        }

        if (resultCode != Activity.RESULT_OK ||
            data == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
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
                .setContentText(
                    "Đang duy trì phiên chụp màn hình"
                )
                .setOngoing(true)
                .build();

            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    1002,
                    notification,
                    android.content.pm.ServiceInfo
                        .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                );
            } else {
                startForeground(1002, notification);
            }

            beginSession(resultCode, data);

        } catch (Exception e) {
            error("Không bắt đầu được: " + e.getMessage());
            stopSelf();
        }

        return START_STICKY;
    }

    private void beginSession(int code, Intent data) {

        android.util.DisplayMetrics dm =
            new android.util.DisplayMetrics();

        wm.getDefaultDisplay().getRealMetrics(dm);

        width = dm.widthPixels;
        height = dm.heightPixels;

        reader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            3
        );

        MediaProjectionManager manager =
            (MediaProjectionManager)
            getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            );

        projection = manager.getMediaProjection(
            code, data
        );

        projection.registerCallback(
            new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    active = false;
                    handler.post(() -> stopSelf());
                }
            },
            handler
        );

        reader.setOnImageAvailableListener(
            r -> {
                Image image = null;

                try {
                    image = r.acquireLatestImage();

                    if (image == null) return;

                    if (!pending ||
                        SystemClock.uptimeMillis() <
                            captureAfter) {
                        return;
                    }

                    Image.Plane plane =
                        image.getPlanes()[0];

                    ByteBuffer buffer =
                        plane.getBuffer();

                    int pixelStride =
                        plane.getPixelStride();

                    int rowStride =
                        plane.getRowStride();

                    int paddedWidth =
                        width +
                        (rowStride -
                            pixelStride * width)
                            / pixelStride;

                    Bitmap padded =
                        Bitmap.createBitmap(
                            paddedWidth,
                            height,
                            Bitmap.Config.ARGB_8888
                        );

                    padded.copyPixelsFromBuffer(buffer);

                    Bitmap fresh =
                        Bitmap.createBitmap(
                            padded, 0, 0, width, height
                        );

                    padded.recycle();

                    pending = false;

                    if (screenshot != null) {
                        screenshot.recycle();
                    }

                    screenshot = fresh;

                    handler.post(
                        () -> showCropOverlay()
                    );

                } catch (Exception e) {
                    pending = false;
                    error(e.getMessage());
                    showCat();
                } finally {
                    if (image != null) {
                        image.close();
                    }
                }
            },
            handler
        );

        display = projection.createVirtualDisplay(
            "KhoanhChupV7",
            width,
            height,
            dm.densityDpi,
            DisplayManager
                .VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.getSurface(),
            null,
            handler
        );

        active = true;

        Toast.makeText(
            this,
            "Đã bắt đầu phiên chụp",
            Toast.LENGTH_SHORT
        ).show();
    }

    private void requestSnapshot() {
        if (!active || pending || overlay != null) {
            return;
        }

        pending = true;

        Intent hide = new Intent(
            this, FloatingCaptureService.class
        );
        hide.setAction("HIDE");
        startService(hide);

        captureAfter =
            SystemClock.uptimeMillis() + 450;

        handler.postDelayed(() -> {
            if (pending) {
                pending = false;
                error("Không nhận được ảnh màn hình");
                showCat();
            }
        }, 5000);
    }

    private void showCropOverlay() {
        if (destroyed || screenshot == null ||
            overlay != null) {
            return;
        }

        try {
            FrameLayout root =
                new FrameLayout(this);

            root.setBackgroundColor(Color.BLACK);

            CropView crop =
                new CropView(this, screenshot);

            root.addView(
                crop,
                new FrameLayout.LayoutParams(
                    -1, -1
                )
            );

            LinearLayout buttons =
                new LinearLayout(this);

            buttons.setGravity(Gravity.CENTER);
            buttons.setBackgroundColor(0xDD202020);

            Button cancel = new Button(this);
            cancel.setText("HỦY");

            Button save = new Button(this);
            save.setText("CẮT VÀ LƯU");

            buttons.addView(cancel);
            buttons.addView(save);

            FrameLayout.LayoutParams bp =
                new FrameLayout.LayoutParams(
                    -1, -2, Gravity.BOTTOM
                );

            root.addView(buttons, bp);

            WindowManager.LayoutParams wp =
                new WindowManager.LayoutParams(
                    -1,
                    -1,
                    WindowManager.LayoutParams
                        .TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams
                        .FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                );

            wm.addView(root, wp);
            overlay = root;

            cancel.setOnClickListener(
                v -> closeCrop()
            );

            save.setOnClickListener(v -> {
                Rect rect = crop.getSelection();

                if (rect == null) {
                    Toast.makeText(
                        this,
                        "Hãy kéo tay chọn vùng cần cắt",
                        Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                try {
                    Bitmap result =
                        Bitmap.createBitmap(
                            screenshot,
                            rect.left,
                            rect.top,
                            rect.width(),
                            rect.height()
                        );

                    saveImage(result);
                    result.recycle();

                    Toast.makeText(
                        this,
                        "Đã lưu vào Pictures/KhoanhChup",
                        Toast.LENGTH_LONG
                    ).show();

                    closeCrop();

                } catch (Exception e) {
                    error(e.getMessage());
                }
            });

        } catch (Exception e) {
            error(e.getMessage());
            closeCrop();
        }
    }

    private void closeCrop() {
        if (overlay != null) {
            try {
                wm.removeView(overlay);
            } catch (Exception ignored) {}

            overlay = null;
        }

        if (screenshot != null) {
            screenshot.recycle();
            screenshot = null;
        }

        showCat();
    }

    private void showCat() {
        if (destroyed) return;

        Intent show = new Intent(
            this, FloatingCaptureService.class
        );

        show.setAction("SHOW");

        try {
            startService(show);
        } catch (Exception ignored) {}
    }

    private void saveImage(Bitmap bitmap)
        throws Exception {

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

        values.put(
            MediaStore.Images.Media.RELATIVE_PATH,
            "Pictures/KhoanhChup"
        );

        values.put(
            MediaStore.Images.Media.IS_PENDING,
            1
        );

        android.net.Uri uri =
            getContentResolver().insert(
                MediaStore.Images.Media
                    .EXTERNAL_CONTENT_URI,
                values
            );

        if (uri == null) {
            throw new Exception(
                "Không tạo được file ảnh"
            );
        }

        boolean success = false;

        try {
            try (OutputStream out =
                getContentResolver()
                    .openOutputStream(uri)) {

                if (out == null ||
                    !bitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        out
                    )) {
                    throw new Exception(
                        "Không lưu được ảnh"
                    );
                }
            }

            values.clear();

            values.put(
                MediaStore.Images.Media.IS_PENDING,
                0
            );

            getContentResolver().update(
                uri, values, null, null
            );

            success = true;

        } finally {
            if (!success) {
                getContentResolver().delete(
                    uri, null, null
                );
            }
        }
    }

    private void error(String message) {
        handler.post(() ->
            Toast.makeText(
                this,
                "Lỗi chụp: " + message,
                Toast.LENGTH_LONG
            ).show()
        );
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        active = false;
        pending = false;

        handler.removeCallbacksAndMessages(null);

        if (overlay != null) {
            try {
                wm.removeView(overlay);
            } catch (Exception ignored) {}
            overlay = null;
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
            MediaProjection old = projection;
            projection = null;
            old.stop();
        }

        if (screenshot != null) {
            screenshot.recycle();
            screenshot = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static class CropView extends View {

        private final Bitmap image;
        private final Paint paint = new Paint(3);

        private float x1, y1, x2, y2;
        private boolean selected = false;

        CropView(Context context, Bitmap bitmap) {
            super(context);
            image = bitmap;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.FILL);

            canvas.drawBitmap(
                image,
                null,
                new Rect(
                    0, 0, getWidth(), getHeight()
                ),
                paint
            );

            if (selected) {
                paint.setColor(Color.GREEN);
                paint.setStrokeWidth(5);
                paint.setStyle(Paint.Style.STROKE);

                canvas.drawRect(
                    x1, y1, x2, y2, paint
                );

                paint.setStyle(Paint.Style.FILL);
            }
        }

        @Override
        public boolean onTouchEvent(
            MotionEvent event
        ) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    x1 = x2 = event.getX();
                    y1 = y2 = event.getY();
                    selected = true;
                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                    x2 = event.getX();
                    y2 = event.getY();
                    invalidate();
                    return true;
            }

            return true;
        }

        Rect getSelection() {
            if (!selected ||
                getWidth() == 0 ||
                getHeight() == 0) {
                return null;
            }

            float sx =
                (float) image.getWidth() /
                getWidth();

            float sy =
                (float) image.getHeight() /
                getHeight();

            int left = (int)(
                Math.min(x1, x2) * sx
            );

            int top = (int)(
                Math.min(y1, y2) * sy
            );

            int right = (int)(
                Math.max(x1, x2) * sx
            );

            int bottom = (int)(
                Math.max(y1, y2) * sy
            );

            left = Math.max(
                0,
                Math.min(
                    left, image.getWidth() - 1
                )
            );

            top = Math.max(
                0,
                Math.min(
                    top, image.getHeight() - 1
                )
            );

            right = Math.max(
                left + 1,
                Math.min(
                    right, image.getWidth()
                )
            );

            bottom = Math.max(
                top + 1,
                Math.min(
                    bottom, image.getHeight()
                )
            );

            if (right - left < 10 ||
                bottom - top < 10) {
                return null;
            }

            return new Rect(
                left, top, right, bottom
            );
        }
    }
}
