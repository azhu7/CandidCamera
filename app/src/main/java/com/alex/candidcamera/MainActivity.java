package com.alex.candidcamera;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements IPictureCaptureListener, ActivityCompat.OnRequestPermissionsResultCallback {

    public static final int PERMISSIONS_REQUEST_ACCESS_CODE = 1;
    private static final int TAKE_PHOTO_CODE = 1;

    Button startButton;
    TextView waitingText;

    String currentImageName;
    private PictureCaptureService pictureService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startButton = (Button) findViewById(R.id.startButton);
        waitingText = (TextView) findViewById(R.id.waitingText);

        startButton.setOnClickListener(this::initialPhoto);
        startButton.setVisibility(View.VISIBLE);
        waitingText.setVisibility(View.INVISIBLE);

        checkPermissions();
        pictureService = PictureCaptureServiceImpl.getInstance(this);
    }

    public void initialPhoto(View v) {
        startButton.setVisibility(View.INVISIBLE);  // Don't need button anymore
        takePhoto();
    }

    private void takePhoto() {
        final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(getTempFile(genImageName())));
        startActivityForResult(intent, TAKE_PHOTO_CODE);
    }

    private void showToast(final String text) {
        runOnUiThread(() ->
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDoneCapturingAllPhotos(Iterable<String> photos) {
        showToast("Done capturing all photos!");
        for (String photo : photos) {
            // Update pictures to show in gallery
            MediaScannerConnection.scanFile(getApplicationContext(),
                    new String[]{photo},
                    null,
                    (path, uri) -> showToast("Scan for " + path + " completed"));
        }

        takePhoto();  // Take another photo.
    }

    private File getTempFile(String imageName) {
        final File path = Environment.getExternalStorageDirectory();
        if (!path.exists()) {
            path.mkdir();
        }
        return new File(path, imageName);
    }

    private String genImageName() {
        this.currentImageName = "Image-" + System.currentTimeMillis() + ".jpg";
        return this.currentImageName;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        pictureService.startCapturing(this);
        waitingText.setVisibility(View.VISIBLE);

        if (resultCode == RESULT_OK) {
            switch(requestCode){
                case TAKE_PHOTO_CODE:
                    final File file = getTempFile(this.currentImageName);
                    showToast(file.getPath());
                    try {
                        // Save image to Android emulated storage
                        Bitmap captureBmp = Media.getBitmap(getContentResolver(), Uri.fromFile(file));
                        FileOutputStream out = new FileOutputStream(file);
                        captureBmp.compress(Bitmap.CompressFormat.JPEG, 90, out);
                        out.flush();
                        out.close();
                        showToast("Refreshing gallery");

                        // Update pictures to show in gallery
                        MediaScannerConnection.scanFile(getApplicationContext(),
                                new String[]{file.getAbsolutePath()},
                                null,
                                (path, uri) -> showToast("Scan for " + path + " completed"));
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    break;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_CODE: {
                if (!(grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    checkPermissions();
                }
            }
        }
    }

    private void checkPermissions() {
        final String[] requiredPermissions = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA,
        };
        final List<String> neededPermissions = new ArrayList<>();
        for (final String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(getApplicationContext(),
                    permission) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(permission);
            }
        }
        if (!neededPermissions.isEmpty()) {
            requestPermissions(neededPermissions.toArray(new String[]{}),
                    PERMISSIONS_REQUEST_ACCESS_CODE);
        }
    }
}
