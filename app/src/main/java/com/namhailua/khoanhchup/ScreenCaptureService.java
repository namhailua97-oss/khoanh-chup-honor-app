package com.namhailua.khoanhchup;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {

    private static final String CHANNEL = "khoanh_capture";

    private Handler handler = new Handler(
        Looper.getMainLooper());

    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private WindowManager wm;
    private View cropOverlay;
    private Bitmap screenshot;
    private boolean captured = false;

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel =
                new NotificationChannel(
                    CHANNEL,
                    "Chup man hinh",
                    NotificationManager.IMPORTANCE_LOW);

            getSystemService(NotificationManager.class)
                .createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(
        Intent intent, int flags, int startId) {

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Khoanh Chup HONOR")
            .setContentText("Dang chup man hinh")
            .build();

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    1002,
                    notification,
                    android.content.pm.ServiceInfo
                        .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(1002, notification);
            }

            if (intent == null) {
                stopSelf();
                return START_NOT_STICKY;
            }

            int resultCode = intent.getIntExtra(
                "resultCode", Activity.RESULT_CANCELED);

            Intent data;

            if (Build.VERSION.SDK_INT >= 33) {
                data = intent.getParcelableExtra(
                    "resultData", Intent.class);
            } else {
                data = intent.getParcelableExtra("resultData");
            }

            if (resultCode != Activity.RESULT_OK ||
                data == null) {
                stopSelf();
                return START_NOT_STICKY;
            }

            Intent hide = new Intent(
                this, FloatingCaptureService.class);
            hide.setAction("HIDE");
            startService(hide);

            handler.postDelayed(
                () -> beginCapture(resultCode, data),
                800);

        } catch (Exception e) {
            showError(e.getMessage());
            finishCapture();
        }

        return START_NOT_STICKY;
    }

    private void beginCapture(int code, Intent data) {
        try {
            wm = (WindowManager)
                getSystemService(WINDOW_SERVICE);

            android.util.DisplayMetrics metrics =
                new android.util.DisplayMetrics();

            wm.getDefaultDisplay().getRealMetrics(metrics);

            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            int density = metrics.densityDpi;

            reader = ImageReader.newInstance(
                width, height,
                PixelFormat.RGBA_8888, 2);

            MediaProjectionManager manager =
                (MediaProjectionManager)
                getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE);

            projection = manager.getMediaProjection(
                code, data);

            projection.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        releaseProjection();
                    }
                }, handler);

            reader.setOnImageAvailableListener(r -> {
                if (captured) return;

                Image image = null;

                try {
                    image = r.acquireLatestImage();
                    if (image == null) return;

                    Image.Plane plane = image.getPlanes()[0];

                    ByteBuffer buffer = plane.getBuffer();

                    int pixelStride = plane.getPixelStride();
                    int rowStride = plane.getRowStride();

                    int paddedWidth = width +
                        (rowStride - pixelStride * width)
                        / pixelStride;

                    Bitmap padded = Bitmap.createBitmap(
                        paddedWidth,
                        height,
                        Bitmap.Config.ARGB_8888);

                    padded.copyPixelsFromBuffer(buffer);

                    screenshot = Bitmap.createBitmap(
                        padded, 0, 0, width, height);

                    padded.recycle();

                    captured = true;

                } catch (Exception e) {
                    showError(e.getMessage());
                } finally {
                    if (image != null) image.close();
                }

                if (captured) {
                    releaseProjection();
                    handler.post(() -> showCropOverlay());
                }
            }, handler);

            display = projection.createVirtualDisplay(
                "KhoanhChup",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(),
                null, handler);

            handler.postDelayed(() -> {
                if (!captured) {
                    showError("Khong nhan duoc anh chup");
                    finishCapture();
                }
            }, 5000);

        } catch (Exception e) {
            showError(e.getMessage());
            finishCapture();
        }
    }

    private void releaseProjection() {
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
    }

    private void showCropOverlay() {
        if (screenshot == null) {
            finishCapture();
            return;
        }

        try {
            FrameLayout root = new FrameLayout(this);
            root.setBackgroundColor(Color.BLACK);

            CropView crop = new CropView(this, screenshot);
            root.addView(crop);

            LinearLayout controls = new LinearLayout(this);
            controls.setOrientation(
                LinearLayout.HORIZONTAL);
            controls.setGravity(Gravity.CENTER);
            controls.setPadding(8, 8, 8, 8);
            controls.setBackgroundColor(0xDD202020);

            Button cancel = new Button(this);
            cancel.setText("HUY");
            controls.addView(cancel);

            Button save = new Button(this);
            save.setText("CAT VA LUU");
            controls.addView(save);

            FrameLayout.LayoutParams buttons =
                new FrameLayout.LayoutParams(
                    -1, -2, Gravity.BOTTOM);

            root.addView(controls, buttons);

            WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                    -1, -1,
                    WindowManager.LayoutParams
                        .TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams
                        .FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);

            wm.addView(root, params);
            cropOverlay = root;

            cancel.setOnClickListener(v ->
                finishCapture());

            save.setOnClickListener(v -> {
                Rect selection = crop.getSelection();

                if (selection == null) {
                    Toast.makeText(this,
                        "Hay keo tay chon vung can cat",
                        Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    Bitmap result = Bitmap.createBitmap(
                        screenshot,
                        selection.left,
                        selection.top,
                        selection.width(),
                        selection.height());

                    saveImage(result);
                    result.recycle();

                    Toast.makeText(this,
                        "Da luu anh vao KhoanhChup",
                        Toast.LENGTH_LONG).show();

                    finishCapture();

                } catch (Exception e) {
                    showError(e.getMessage());
                }
            });

        } catch (Exception e) {
            showError(e.getMessage());
            finishCapture();
        }
    }

    private void saveImage(Bitmap bitmap)
        throws Exception {

        ContentValues values = new ContentValues();

        values.put(
            MediaStore.Images.Media.DISPLAY_NAME,
            "KhoanhChup_" +
                System.currentTimeMillis() + ".png");

        values.put(
            MediaStore.Images.Media.MIME_TYPE,
            "image/png");

        values.put(
            MediaStore.Images.Media.RELATIVE_PATH,
            "Pictures/KhoanhChup");

        values.put(
            MediaStore.Images.Media.IS_PENDING, 1);

        android.net.Uri uri =
            getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values);

        if (uri == null) {
            throw new Exception("Khong tao duoc tep anh");
        }

        boolean success = false;

        try {
            try (OutputStream out =
                getContentResolver().openOutputStream(uri)) {

                if (out == null ||
                    !bitmap.compress(
                        Bitmap.CompressFormat.PNG, 100, out)) {
                    throw new Exception("Khong ghi duoc anh");
                }
            }

            values.clear();
            values.put(
                MediaStore.Images.Media.IS_PENDING, 0);

            getContentResolver().update(
                uri, values, null, null);

            success = true;

        } finally {
            if (!success) {
                getContentResolver().delete(
                    uri, null, null);
            }
        }
    }

    private void showError(String message) {
        Toast.makeText(this,
            "Loi chup: " + message,
            Toast.LENGTH_LONG).show();
    }

    private void finishCapture() {
        if (cropOverlay != null && wm != null) {
            try {
                wm.removeView(cropOverlay);
            } catch (Exception ignored) {}
            cropOverlay = null;
        }

        releaseProjection();

        if (screenshot != null) {
            screenshot.recycle();
            screenshot = null;
        }

        Intent show = new Intent(
            this, FloatingCaptureService.class);
        show.setAction("SHOW");

        try {
            startService(show);
        } catch (Exception ignored) {}

        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (cropOverlay != null && wm != null) {
            try {
                wm.removeView(cropOverlay);
            } catch (Exception ignored) {}
            cropOverlay = null;
        }

        releaseProjection();

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

        private Bitmap image;
        private Paint paint = new Paint(3);
        private float startX, startY, endX, endY;
        private boolean selected = false;

        public CropView(Context context, Bitmap bitmap) {
            super(context);
            image = bitmap;
            setBackgroundColor(Color.BLACK);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (image == null) return;

            canvas.drawBitmap(
                image, null,
                new Rect(0, 0, getWidth(), getHeight()),
                paint);

            if (selected) {
                paint.setColor(Color.GREEN);
                paint.setStrokeWidth(5);
                paint.setStyle(Paint.Style.STROKE);

                canvas.drawRect(
                    startX, startY, endX, endY, paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
            }
        }

        @Override
        public boolean onTouchEvent(
            android.view.MotionEvent event) {

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getX();
                    startY = event.getY();
                    endX = startX;
                    endY = startY;
                    selected = true;
                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                    endX = event.getX();
                    endY = event.getY();
                    invalidate();
                    return true;
            }
            return true;
        }

        public Rect getSelection() {
            if (!selected || image == null ||
                getWidth() == 0 || getHeight() == 0) {
                return null;
            }

            float scaleX =
                (float) image.getWidth() / getWidth();

            float scaleY =
                (float) image.getHeight() / getHeight();

            int left = (int)(
                Math.min(startX, endX) * scaleX);

            int top = (int)(
                Math.min(startY, endY) * scaleY);

            int right = (int)(
                Math.max(startX, endX) * scaleX);

            int bottom = (int)(
                Math.max(startY, endY) * scaleY);

            left = Math.max(0, Math.min(
                left, image.getWidth() - 1));

            top = Math.max(0, Math.min(
                top, image.getHeight() - 1));

            right = Math.max(left + 1, Math.min(
                right, image.getWidth()));

            bottom = Math.max(top + 1, Math.min(
                bottom, image.getHeight()));

            if (right - left < 10 ||
                bottom - top < 10) {
                return null;
            }

            return new Rect(left, top, right, bottom);
        }
    }
                }
