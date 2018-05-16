package com.alex.candidcamera;

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.util.SparseIntArray;
import android.view.Surface;

public abstract class PictureCaptureService {
    private static final SparseIntArray Orientations = new SparseIntArray();

    static {
        Orientations.append(Surface.ROTATION_0, 270);
        Orientations.append(Surface.ROTATION_90, 0);
        Orientations.append(Surface.ROTATION_180, 90);
        Orientations.append(Surface.ROTATION_270, 180);
    }

    private final Activity activity;
    final Context context;
    final CameraManager manager;

    PictureCaptureService(final Activity activity) {
        this.activity = activity;
        this.context = activity.getApplicationContext();
        this.manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    int getOrientation() {
        final int rotation = this.activity.getWindowManager().getDefaultDisplay().getRotation();
        return Orientations.get(rotation);
    }

    public abstract void startCapturing(final IPictureCaptureListener listener);
}
