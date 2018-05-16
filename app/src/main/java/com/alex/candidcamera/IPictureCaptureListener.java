package com.alex.candidcamera;

import java.util.TreeMap;

public interface IPictureCaptureListener {
    // Callback called when done taking pictures from all available cameras
    // or when no camera was detected on the device
    void onDoneCapturingAllPhotos(Iterable<String> photos);
}
