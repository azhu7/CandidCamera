package com.alex.candidcamera;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.os.Handler;
import android.util.Size;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class PictureCaptureServiceImpl extends PictureCaptureService {
    private static final String TAG = PictureCaptureService.class.getSimpleName();

    private CameraDevice cameraDevice;
    private ImageReader imageReader;
    private String currentCameraId;
    private boolean cameraClosed;
    private IPictureCaptureListener captureListener;
    private ArrayList<String> picturesTaken;

    private PictureCaptureServiceImpl(final Activity activity) {
        super(activity);

        picturesTaken = new ArrayList<>();
    }

    public static PictureCaptureService getInstance(final Activity activity) {
        return new PictureCaptureServiceImpl(activity);
    }

    @Override
    public void startCapturing(final IPictureCaptureListener listener) {
        this.captureListener = listener;
        try {
            final String[] cameraIds = manager.getCameraIdList();
            for (String cameraId : cameraIds) {
                boolean isFrontFacing = manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
                if (!isFrontFacing) {
                    continue;
                }

                this.currentCameraId = cameraId;
                openCamera();
            }

            if (cameraIds.length == 0) {
                captureListener.onDoneCapturingAllPhotos(this.picturesTaken);
            }
        } catch(final CameraAccessException e){
            Log.e(TAG, "startCapturing()", e);
        }
    }

    private void openCamera() {
        Log.d(TAG, "Opening camera " + currentCameraId);
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                manager.openCamera(currentCameraId, stateCallback, null);
            }
        } catch (final Exception e) {
            Log.e(TAG, "openCamera()", e);
        }
    }

    private final CameraCaptureSession.CaptureCallback cameraCaptureListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Log.i(TAG, "captureListener: Done taking picture from camera " + cameraDevice.getId());
            closeCamera();
        }
    };

    private final ImageReader.OnImageAvailableListener onImageAvailableListener = (ImageReader imReader) -> {
        final Image image = imReader.acquireLatestImage();
        final ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        final byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        saveImageToDisk(bytes);
        image.close();
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraClosed = false;
            Log.d(TAG, "Camera " + camera.getId() + " opened");
            cameraDevice = camera;
            new Handler().postDelayed(() -> {
                try {
                    takePicture();
                } catch (final CameraAccessException e) {
                    Log.e(TAG, "stateCallback", e);
                }
            }, 500);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera " + camera.getId() + " disconnected");
            if (cameraDevice != null && !cameraClosed) {
                cameraClosed = true;
                cameraDevice.close();
            }
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            cameraClosed = true;
            Log.d(TAG, "Camera " + camera.getId() + " closed");
            captureListener.onDoneCapturingAllPhotos(picturesTaken);
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "Camera error, int code " + error);
            if (cameraDevice != null && !cameraClosed) {
                cameraClosed = true;
                cameraDevice.close();
            }
        }
    };

    private void takePicture() throws CameraAccessException {
        if (cameraDevice == null) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }

        final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
        Size[] jpegSizes = null;
        StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (streamConfigurationMap != null) {
            jpegSizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
        }

        final boolean jpegSizesNotEmpty = jpegSizes != null && 0 < jpegSizes.length;
        int width = jpegSizesNotEmpty ? jpegSizes[0].getWidth() : 640;
        int height = jpegSizesNotEmpty ? jpegSizes[0].getHeight() : 480;
        final ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
        final List<Surface> outputSurfaces = new ArrayList<>();
        outputSurfaces.add(reader.getSurface());

        final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        captureBuilder.addTarget(reader.getSurface());
        captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation());
        reader.setOnImageAvailableListener(onImageAvailableListener, null);
        cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                try {
                    session.capture(captureBuilder.build(), cameraCaptureListener, null);
                } catch (final CameraAccessException e) {
                    Log.e(TAG, "onConfigured()", e);
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
        }, null);
    }

    private File getTempFile(String imageName) {
        final File path = Environment.getExternalStorageDirectory();
        if (!path.exists()) {
            path.mkdir();
        }
        return new File(path, imageName);
    }

    private void saveImageToDisk(final byte[] bytes) {
        final File file = getTempFile("Candid-" + System.currentTimeMillis() + ".jpg");
        try (final OutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
            this.picturesTaken.add(file.getAbsolutePath());
        } catch (final IOException e) {
            Log.e(TAG, "saveImageToDisk()", e);
        }
    }

    private void closeCamera() {
        if (cameraDevice != null && !cameraClosed) {
            cameraDevice.close();
            cameraDevice = null;
        }

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }
}
