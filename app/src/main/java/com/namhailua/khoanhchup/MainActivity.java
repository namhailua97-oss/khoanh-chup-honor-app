package com.namhailua.khoanhchup;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int PICK = 100;
    private ImageView imageView;
    private FrameLayout frame;
    private DrawView drawView;
    private Bitmap bitmap;
    private TextView status;
    private boolean freeMode = false;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(12, 24, 12, 12);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("KHOANH CHỤP");
        title.setTextSize(26);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        frame = new FrameLayout(this);
        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.addView(imageView, new FrameLayout.LayoutParams(-1, -1));

        drawView = new DrawView();
        frame.addView(drawView, new FrameLayout.LayoutParams(-1, -1));

        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));

        Button choose = button("CHỌN ẢNH");
        choose.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(i, PICK);
        });
        root.addView(choose);

        Button mode = button("CHẾ ĐỘ: HÌNH CHỮ NHẬT");
        mode.setOnClickListener(v -> {
            freeMode = !freeMode;
            drawView.clear();
            mode.setText(freeMode ?
                "CHẾ ĐỘ: VẼ TỰ DO" :
                "CHẾ ĐỘ: HÌNH CHỮ NHẬT");
        });
        root.addView(mode);

        Button save = button("CẮT VÀ LƯU ẢNH");
        save.setOnClickListener(v -> saveSelection());
        root.addView(save);

        Button clear = button("XÓA VÙNG KHOANH");
        clear.setOnClickListener(v -> drawView.clear());
        root.addView(clear);

        status = new TextView(this);
        status.setText("Chọn ảnh rồi dùng tay khoanh vùng");
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        setContentView(root);
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK && res == RESULT_OK && data != null) {
            try {
                Uri uri = data.getData();
                Bitmap loaded;
                try (java.io.InputStream in =
                         getContentResolver().openInputStream(uri)) {
                    loaded = BitmapFactory.decodeStream(in);
                }
                if (loaded == null) throw new Exception("Không đọc được ảnh");
                bitmap = loaded;
                imageView.setImageBitmap(bitmap);
                drawView.clear();
                status.setText("Dùng ngón tay khoanh vùng cần lấy");
            } catch (Exception e) {
                status.setText("Lỗi mở ảnh: " + e.getMessage());
            }
        }
    }

    private void saveSelection() {
        if (bitmap == null || !drawView.hasSelection) {
            status.setText("Hãy chọn ảnh và khoanh vùng trước");
            return;
        }
        try {
            RectF displayed = new RectF();
            Matrix m = imageView.getImageMatrix();
            displayed.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
            m.mapRect(displayed);
            displayed.offset(imageView.getLeft(), imageView.getTop());

            float sx = bitmap.getWidth() / displayed.width();
            float sy = bitmap.getHeight() / displayed.height();

            RectF selected = drawView.getBounds();
            selected.intersect(displayed);

            int left = Math.max(0, (int)((selected.left - displayed.left) * sx));
            int top = Math.max(0, (int)((selected.top - displayed.top) * sy));
            int right = Math.min(bitmap.getWidth(),
                (int)Math.ceil((selected.right - displayed.left) * sx));
            int bottom = Math.min(bitmap.getHeight(),
                (int)Math.ceil((selected.bottom - displayed.top) * sy));

            if (right <= left || bottom <= top)
                throw new Exception("Vùng chọn không hợp lệ");

            Bitmap output = Bitmap.createBitmap(
                right - left, bottom - top, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);

            if (freeMode) {
                Path mask = new Path(drawView.path);
                Matrix transform = new Matrix();
                transform.setTranslate(-displayed.left, -displayed.top);
                transform.postScale(sx, sy);
                transform.postTranslate(-left, -top);
                mask.transform(transform);
                canvas.clipPath(mask);
            }

            canvas.drawBitmap(bitmap,
                new Rect(left, top, right, bottom),
                new Rect(0, 0, output.getWidth(), output.getHeight()),
                null);

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,
                "KhoanhChup_" + System.currentTimeMillis() + ".png");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= 29) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/KhoanhChup");
            }

            Uri uri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("Không tạo được tệp");

            try (OutputStream out =
                     getContentResolver().openOutputStream(uri)) {
                if (!output.compress(Bitmap.CompressFormat.PNG, 100, out))
                    throw new Exception("Không lưu được PNG");
            }
            status.setText("Đã lưu ảnh thành công!");
        } catch (Exception e) {
            status.setText("Lỗi: " + e.getMessage());
        }
    }

    class DrawView extends View {
        Paint paint = new Paint(3);
        Path path = new Path();
        RectF rect = new RectF();
        float startX, startY;
        boolean hasSelection = false;
        ArrayList<PointF> points = new ArrayList<>();

        DrawView() {
            super(MainActivity.this);
            paint.setColor(Color.RED);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
        }

        void clear() {
            path.reset();
            rect.setEmpty();
            points.clear();
            hasSelection = false;
            invalidate();
        }

        RectF getBounds() {
            if (!freeMode) return new RectF(rect);
            RectF r = new RectF();
            path.computeBounds(r, true);
            return r;
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (!hasSelection) return;
            if (freeMode) c.drawPath(path, paint);
            else c.drawRect(rect, paint);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            if (bitmap == null) return false;
            float x = e.getX(), y = e.getY();

            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    clear();
                    startX = x;
                    startY = y;
                    hasSelection = true;
                    if (freeMode) path.moveTo(x, y);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (freeMode) path.lineTo(x, y);
                    else rect.set(
                        Math.min(startX, x), Math.min(startY, y),
                        Math.max(startX, x), Math.max(startY, y));
                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP:
                    if (freeMode) {
                        path.lineTo(x, y);
                        path.close();
                    } else {
                        rect.set(
                            Math.min(startX, x), Math.min(startY, y),
                            Math.max(startX, x), Math.max(startY, y));
                    }
                    invalidate();
                    return true;
            }
            return true;
        }
    }
}
