package com.dual_camera_video_mock;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class AudioVideoRecording extends AppCompatActivity {

    private MyGLSurfaceView cameraView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraView = new MyGLSurfaceView(this);
        setContentView(cameraView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(cameraView!=null)
        cameraView.closeCamera();
    }
}
