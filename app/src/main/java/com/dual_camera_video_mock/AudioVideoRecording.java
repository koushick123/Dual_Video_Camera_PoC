package com.dual_camera_video_mock;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;

public class AudioVideoRecording extends AppCompatActivity {

    private MyGLSurfaceView cameraView;
    private int MY_PERMISSIONS_REQUEST_CAMERA=0;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == MY_PERMISSIONS_REQUEST_CAMERA){
            //requestWindowFeature ( Window.FEATURE_NO_TITLE);
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
            //requestWindowFeature ( Window.FEATURE_NO_TITLE);
            getSupportActionBar().hide();
            cameraView = new MyGLSurfaceView(this);
            LinearLayout parentLinearLayout = new LinearLayout(this);
            parentLinearLayout.setOrientation(LinearLayout.HORIZONTAL);

            //Top Bar
            LinearLayout topBar = new LinearLayout(this);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            layoutParams.height=720;
            layoutParams.width=216;
            topBar.setOrientation(LinearLayout.VERTICAL);
            topBar.setBackgroundColor(Color.BLACK);
            topBar.setLayoutParams(layoutParams);

            //Bottom bar
            LinearLayout bottomBar = new LinearLayout(this);
            ImageButton cameraButton = new ImageButton(this);
            cameraButton.setImageResource(R.drawable.ic_photo_camera);
            bottomBar.setGravity(Gravity.CENTER);
            bottomBar.addView(cameraButton);
            bottomBar.setOrientation(LinearLayout.VERTICAL);
            bottomBar.setBackgroundColor(Color.BLACK);
            bottomBar.setLayoutParams(layoutParams);

            //Add them to parent
            parentLinearLayout.addView(topBar);
            parentLinearLayout.addView(cameraView);
            parentLinearLayout.addView(bottomBar);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            setContentView(parentLinearLayout);
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
