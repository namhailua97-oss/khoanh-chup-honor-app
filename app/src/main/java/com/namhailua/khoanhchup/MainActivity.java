
package com.namhailua.khoanhchup;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.net.Uri;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;
import android.graphics.Color;
import android.graphics.Typeface;

import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {

    private static final int TAKE_PHOTO = 100;
    private static final int PICK_IMAGE = 101;

    private ImageView imageView;
    private TextView status;
    private Bitmap currentImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(35, 55, 35, 30);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("KHOANH CHUP");
        title.setTextSize(27);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);

        layout.addView(title);

        TextView description = new TextView(this);
        description.setText("Ung dung chup va quan ly anh");
        description.setTextSize(15);
        description.setGravity(Gravity.CENTER);
        description.setPadding(0, 15, 0, 25);

        layout.addView(description);

        imageView = new ImageView(this);
        imageView.setAdjustViewBounds(true);
        imageView.setMaxHeight(650);
        imageView.setMinimumHeight(250);
        imageView.setScaleType(
            ImageView.ScaleType.FIT_CENTER
        );

        layout.addView(imageView);

        Button cameraButton = new Button(this);
        cameraButton.setText("CHUP ANH");

        layout.addView(cameraButton);

        Button galleryButton = new Button(this);
        galleryButton.setText("CHON ANH TU THU VIEN");

        layout.addView(galleryButton);

        Button saveButton = new Button(this);
        saveButton.setText("LUU ANH");

        layout.addView(saveButton);

        status = new TextView(this);
        status.setText("San sang");
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 20, 0, 0);

        layout.addView(status);

        setContentView(layout);

        cameraButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                MediaStore.ACTION_IMAGE_CAPTURE
            );

            if (intent.resolveActivity(
                    getPackageManager()) != null) {
                startActivityForResult(
                    intent, TAKE_PHOTO
                );
            } else {
                status.setText(
                    "Khong tim thay ung dung camera"
                );
            }
        });

        galleryButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                Intent.ACTION_GET_CONTENT
            );
            intent.setType("image/*");
            startActivityForResult(
                Intent.createChooser(
                    intent, "Chon anh"
                ),
                PICK_IMAGE
            );
        });

        saveButton.setOnClickListener(v -> {
            saveImage();
        });
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
            requestCode, resultCode, data
        );

        if (resultCode != RESULT_OK ||
                data == null) {
            return;
        }

        try {
            Bitmap bitmap = null;

            if (requestCode == TAKE_PHOTO) {
                Bundle extras = data.getExtras();

                if (extras != null) {
                    Object photo = extras.get("data");

                    if (photo instanceof Bitmap) {
                        bitmap = (Bitmap) photo;
                    }
                }
            }

            if (requestCode == PICK_IMAGE) {
                Uri uri = data.getData();

                if (uri != null) {
                    try (InputStream stream =
                        getContentResolver()
                            .openInputStream(uri)) {

                        bitmap = BitmapFactory
                            .decodeStream(stream);
                    }
                }
            }

            if (bitmap != null) {
                currentImage = bitmap;
                imageView.setImageBitmap(bitmap);
                status.setText("Da tai anh thanh cong");
            } else {
                status.setText("Khong doc duoc anh");
            }

        } catch (Exception e) {
            status.setText(
                "Loi: " + e.getMessage()
            );
        }
    }

    private void saveImage() {

        if (currentImage == null) {
            status.setText("Hay chon anh truoc");
            return;
        }

        try {
            File directory = new File(
                getFilesDir(), "photos"
            );

            if (!directory.exists() &&
                    !directory.mkdirs()) {
                throw new Exception(
                    "Khong tao duoc thu muc"
                );
            }

            File file = new File(
                directory,
                "photo_" +
                System.currentTimeMillis() +
                ".png"
            );

            try (FileOutputStream output =
                    new FileOutputStream(file)) {

                boolean success =
                    currentImage.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        output
                    );

                if (!success) {
                    throw new Exception(
                        "Khong luu duoc anh"
                    );
                }
            }

            status.setText(
                "Da luu anh trong ung dung"
            );

        } catch (Exception e) {
            status.setText(
                "Loi luu anh: " + e.getMessage()
            );
        }
    }
}
