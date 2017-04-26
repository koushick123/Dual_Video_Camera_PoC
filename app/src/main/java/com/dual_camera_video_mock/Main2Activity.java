package com.dual_camera_video_mock;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.IOException;

public class Main2Activity extends AppCompatActivity {

    private Camera mCamera;
    String LOG_TAG = Main2Activity.class.getName();
    private CameraPreview mPreview;
    private MediaRecorder mMediaRecorder;
    Button videoBtn;
    static boolean BACK_CAM = true;
    static int currentCameraId;
    static boolean mIsRecording=false;
    String mNextVideoAbsolutePath;
    FrameLayout preview;
    private SurfaceHolder mHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        if(checkCameraHardware(getApplicationContext())) {
            // Create an instance of Camera
            //Open the back camera first
            if(mCamera == null) {
                mCamera = getCameraInstance(true);
            }
            if(mCamera!=null){
                Log.d(LOG_TAG,"Cameras == "+Camera.getNumberOfCameras());
            }
        }

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(mPreview);
        videoBtn = (Button)findViewById(R.id.button_capture);
        mMediaRecorder = new MediaRecorder();
        videoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!mIsRecording) {
                    startRecording();
                }
                else{
                    stopRecording();
                }
            }
        });

        Button switch_btn = (Button)findViewById(R.id.switch_btn);
        switch_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mCamera.stopPreview();
                releaseCamera();
                if(BACK_CAM){
                    mCamera = getCameraInstance(false);
                    Log.d(LOG_TAG,"switched to front camera == "+mCamera);
                }
                else{
                    mCamera = getCameraInstance(true);
                    Log.d(LOG_TAG,"switched to back camera == "+mCamera);
                }
                if(mIsRecording){
                    startRecording();
                }
                else{
                    createPreview();
                }
            }
        });
    }

    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    public static Camera getCameraInstance(boolean backCam){
        Camera c = null;
        try {
            if(backCam) {
                c = Camera.open(getBackCameraId()); // attempt to get a Camera instance
                BACK_CAM=true;
            }
            else{
                c = Camera.open(getFrontCameraId());
                BACK_CAM=false;
            }
        }
        catch (Exception e){
            e.printStackTrace();
            Log.e(Main2Activity.class.getName(),"Unable to open camera...");
        }
        return c; // returns null if camera is unavailable
    }

    public static int getBackCameraId()
    {
        for(int i=0;i<Camera.getNumberOfCameras();i++)
        {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(i,cameraInfo);
            if(cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK){
                currentCameraId = i;
                return i;
            }
        }
        return 0;
    }

    public static int getFrontCameraId()
    {
        for(int i=0;i<Camera.getNumberOfCameras();i++)
        {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(i,cameraInfo);
            if(cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT){
                currentCameraId = i;
                return i;
            }
        }
        return 0;
    }

    private void startRecording()
    {
        if(!mIsRecording) {
            mCamera.unlock();
            mMediaRecorder.setCamera(mCamera);
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
                mNextVideoAbsolutePath = getVideoFilePath(getApplicationContext());
            }
            mMediaRecorder.setProfile(CamcorderProfile.get(currentCameraId,CamcorderProfile.QUALITY_HIGH));
            mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
            mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());
            try {
                mMediaRecorder.prepare();
                mMediaRecorder.start();
                videoBtn.setText("STOP VIDEO");
                mIsRecording = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getVideoFilePath(Context context) {
        return context.getExternalFilesDir(null).getAbsolutePath() + "/"
                + System.currentTimeMillis() + ".mp4";
    }

    private void stopRecording()
    {
        videoBtn.setText("Take Video");
        mMediaRecorder.stop();
        mIsRecording=false;
        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH){
            mCamera.lock();
        }
        Toast.makeText(getApplicationContext(), "Video saved: " + mNextVideoAbsolutePath,
                Toast.LENGTH_SHORT).show();
        Log.d(LOG_TAG, "Video saved: " + mNextVideoAbsolutePath);
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
            Log.d(LOG_TAG,"Camera released...");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(LOG_TAG,"onPause");
        releaseMediaRecorder();       // if you are using MediaRecorder, release it first
        releaseCamera();              // release the camera immediately on pause event
    }

    @Override
    protected void onResume(){
        super.onResume();
        Log.d(LOG_TAG,"onResume... "+mCamera+", BACK_CAM == "+BACK_CAM);
        if(mCamera == null) {
            if (BACK_CAM) {
                mCamera = getCameraInstance(true);
            } else {
                mCamera = getCameraInstance(false);
            }
        }
    }

    private void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            mCamera.lock();           // lock camera for later use
            Log.d(LOG_TAG,"media recorder released....");
        }
    }

    class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

        public CameraPreview(Context context, Camera camera) {
            super(context);
            mCamera = camera;

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder = getHolder();
            mHolder.addCallback(this);
        }

        public void surfaceCreated(SurfaceHolder holder) {
            // The Surface has been created, now tell the camera where to draw the preview.
            if (mCamera != null) {
                createPreview();
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // empty. Take care of releasing the Camera preview in your activity.
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            // If your preview can change or rotate, take care of those events here.
            // Make sure to stop the preview before resizing or reformatting it.

            if (mHolder.getSurface() == null) {
                // preview surface does not exist
                return;
            }

            // stop preview before making changes
            try {
                mCamera.stopPreview();
            } catch (Exception e) {
                // ignore: tried to stop a non-existent preview
            }

            // set preview size and make any resize, rotate or
            // reformatting changes here
            createPreview();
        }
    }

    private void setCameraDisplayOrientation(Activity activity,
                                                    int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    private void createPreview()
    {
        if (BACK_CAM) {
            setCameraDisplayOrientation(Main2Activity.this, getBackCameraId(), mCamera);
        } else {
            setCameraDisplayOrientation(Main2Activity.this, getFrontCameraId(), mCamera);
        }
        try {
            mCamera.setPreviewDisplay(mHolder);
        } catch (IOException e) {
            Log.e(LOG_TAG,"Unable to recreate preview");
            e.printStackTrace();
        }
        mCamera.startPreview();
    }
}
