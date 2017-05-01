package com.dual_camera_video_mock;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;

public class AudioVideoRecording extends AppCompatActivity {

    private MyGLSurfaceView cameraView;
    private int MY_PERMISSIONS_REQUEST_CAMERA=0;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == MY_PERMISSIONS_REQUEST_CAMERA){
            requestWindowFeature ( Window.FEATURE_NO_TITLE);
            getSupportActionBar().hide();
            cameraView = new MyGLSurfaceView(this);
            setContentView(cameraView);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int permission = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA);
        if(permission == PackageManager.PERMISSION_GRANTED) {
            requestWindowFeature ( Window.FEATURE_NO_TITLE);
            getSupportActionBar().hide();
            cameraView = new MyGLSurfaceView(this);
            setContentView(cameraView);
        }
        else{
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    MY_PERMISSIONS_REQUEST_CAMERA);
        }
    }

    @Override
    protected void onPause() {
        cameraView.onPause();
        super.onPause();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        cameraView.onResume();
    }
}
