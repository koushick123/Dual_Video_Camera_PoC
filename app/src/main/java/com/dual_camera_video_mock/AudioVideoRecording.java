package com.dual_camera_video_mock;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Iterator;
import java.util.List;

public class AudioVideoRecording extends AppCompatActivity implements SurfaceHolder.Callback, SurfaceTexture.OnFrameAvailableListener{

    private int MY_PERMISSIONS_REQUEST_CAMERA=0;
    private int MY_PERMISSIONS_REQUEST_AUDIO=1;

    private AudioRecord audioRecord;
    private MediaCodec mediaCodec;
    CamcorderProfile camcorderProfile;
    final String VIDEO_MIME_TYPE = "video/avc";
    private MediaCodec videoCodec;
    int cameraId;
    volatile boolean isAudioAdded = false;
    volatile boolean isVideoAdded = false;
    Object recordSync = new Object();
    private static int VIDEO_WIDTH = 1280;  // dimensions for 720p video
    private static int VIDEO_HEIGHT = 720;
    private SurfaceView cameraView;
    final int FRAME_AVAILABLE = 1000;
    final int RECORD_STOP = 2000;
    final int RECORD_START = 3000;
    //final int RECORD_COMPLETED = 4000;
    final int GET_RECORDER = 5000;
    final int SHUTDOWN = 6000;
    final int GET_READY = 7000;
    SurfaceTexture surfaceTexture;
    private Camera mCamera;
    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLConfig mEGLConfig = null;
    // Android-specific extension.
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    public static final int FLAG_RECORDABLE = 0x01;
    private int mProgramHandle;
    private int mTextureTarget;
    private int mTextureId;
    private int muMVPMatrixLoc;
    private static final int SIZEOF_FLOAT = 4;
    int frameCount=0;
    private int muTexMatrixLoc;
    private int maPositionLoc;
    private int maTextureCoordLoc;
    /**
     * A "full" square, extending from -1 to +1 in both dimensions.  When the model/view/projection
     * matrix is identity, this will exactly cover the viewport.
     * <p>
     * The texture coordinates are Y-inverted relative to RECTANGLE.  (This seems to work out
     * right with external textures from SurfaceTexture.)
     */
    private static final float FULL_RECTANGLE_COORDS[] = {
            -1.0f, -1.0f,   // 0 bottom left
            1.0f, -1.0f,   // 1 bottom right
            -1.0f,  1.0f,   // 2 top left
            1.0f,  1.0f,   // 3 top right
    };
    private static final float FULL_RECTANGLE_TEX_COORDS[] = {
            0.0f, 0.0f,     // 0 bottom left
            1.0f, 0.0f,     // 1 bottom right
            0.0f, 1.0f,     // 2 top left
            1.0f, 1.0f      // 3 top right
    };
    //Surface onto which camera frames are drawn
    EGLSurface eglSurface;
    Surface videoSurface;
    //Surface to which camera frames are sent for encoding to mp4 format
    EGLSurface encoderSurface=null;
    private final float[] mTmpMatrix = new float[16];
    public static final float[] IDENTITY_MATRIX;
    static {
        IDENTITY_MATRIX = new float[16];
        Matrix.setIdentityM(IDENTITY_MATRIX, 0);
    }

    // Simple vertex shader, used for all programs.
    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
                    "uniform mat4 uTexMatrix;\n" +
                    "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTextureCoord;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = uMVPMatrix * aPosition;\n" +
                    "    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
                    "}\n";

    // Simple fragment shader for use with external 2D textures (e.g. what we get from
    // SurfaceTexture).
    private static final String FRAGMENT_SHADER_EXT =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                    "}\n";

    volatile boolean isRecording=false;
    volatile boolean isCapturing=false;
    final static String MIME_TYPE = "audio/mp4a-latm";
    final static int SAMPLE_RATE = 44100;
    final static int BIT_RATE = 128000;
    public static final int SAMPLES_PER_FRAME = 1024;	// AAC, bytes/frame/channel
    public static final int FRAMES_PER_BUFFER = 25; 	// AAC, frame/buffer/sec
    MediaFormat audioFormat=null;
    MediaFormat videoFormat=null;
    int TIMEOUT = 10000;

    static final String AUDIO_PERMISSION = "android.permission.RECORD_AUDIO";
    static final String CAMERA_PERMISSION = "android.permission.CAMERA";
    final String TAG = this.getClass().getName();
    MediaMuxerHelper mediaMuxerHelper;
    MediaMuxer mediaMuxer;
    RecordVideo.VideoRecordHandler recordHandler=null;
    MainHandler mainHandler;
    Thread audio;
    Thread video;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == MY_PERMISSIONS_REQUEST_CAMERA){
            if(permissions!=null && permissions.length > 0){
                Log.d(TAG,"For camera");
                if(permissions[0].equalsIgnoreCase(CAMERA_PERMISSION)) {
                    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        SurfaceHolder sh = cameraView.getHolder();
                        sh.addCallback(this);
                        //setupCameraPreview();
                    } else {
                        Toast.makeText(getApplicationContext(), "Camera Permission not given. App cannot show Camera preview.", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            else {
                Toast.makeText(getApplicationContext(), "Something wrong with obtaining Camera permissions. App cannot proceed with Camera preview", Toast.LENGTH_SHORT).show();
            }
        }
        else if(requestCode == MY_PERMISSIONS_REQUEST_AUDIO){
            if(permissions!=null && permissions.length > 0){
                Log.d(TAG,"For audio");
                if(!permissions[0].equalsIgnoreCase(AUDIO_PERMISSION)){
                    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                        Toast.makeText(getApplicationContext(),"Audio Record Permission not given. App cannot record audio.",Toast.LENGTH_SHORT).show();
                    }
                }
            }
            else{
                Toast.makeText(getApplicationContext(),"Something wrong with obtaining Microphone permissions. App cannot proceed with Audio record.",Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_video_recording);
        cameraView = (SurfaceView) findViewById(R.id.cameraView);
        mainHandler = new MainHandler(this);
        final ImageButton recordButton = (ImageButton)findViewById(R.id.record_button);
        recordButton.setColorFilter(Color.DKGRAY);
        recordButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view)
            {
                if(!isRecording){
                    recordButton.setColorFilter(Color.RED);
                    //Record here
                    prepareMuxer();
                    /*setupAudioRecorder();
                    try {
                        audio = new Thread(new RecordAudio());
                        audio.start();
                    }
                    catch (Exception e){
                        e.printStackTrace();
                    }*/
                    video = new RecordVideo();
                    video.start();
                    waitUntilReady();
                    recordHandler.sendEmptyMessage(RECORD_START);
                    //isRecording=true;
                    //recordHandler.sendEmptyMessage(GET_READY);
                    //setupVideoRecorder();
                }
                else{
                    isRecording=false;
                    recordButton.setColorFilter(Color.DKGRAY);
                    //Stop here
                        //audio.join(2500);
                        recordHandler.sendEmptyMessage(RECORD_STOP);
                    Log.d(TAG,"Recorder thread EXITED...");
                    //recordHandler.sendEmptyMessage(SHUTDOWN);
                        //recordHandler.sendEmptyMessage(GET_RECORDER);
                    }
            }
        });
        getSupportActionBar().hide();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        checkForPermissions();
        //setupVideoRecorder();
    }

    private void checkForPermissions() {
        int permission = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA);
        if (permission == PackageManager.PERMISSION_GRANTED) {
            SurfaceHolder sh = cameraView.getHolder();
            sh.addCallback(this);
            //setupCameraPreview();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    MY_PERMISSIONS_REQUEST_CAMERA);
        }

        permission = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSIONS_REQUEST_AUDIO);
        }
    }

    private void setupCameraPreview()
    {
        getSupportActionBar().hide();
        LinearLayout parentLinearLayout = new LinearLayout(this);
        parentLinearLayout.setOrientation(LinearLayout.HORIZONTAL);

        //Top Bar
        LinearLayout topBar = new LinearLayout(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layoutParams.height=720;
        layoutParams.width=108;
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setBackgroundColor(Color.BLACK);
        topBar.setLayoutParams(layoutParams);

        //Bottom bar
        LinearLayout bottomBar = new LinearLayout(this);
        ImageButton cameraButton = new ImageButton(this);
        cameraButton.setPadding(0,0,50,0);
        //cameraButton.setImageResource(R.drawable.ic_photo_camera);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.addView(cameraButton);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams layoutParams_bottom = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layoutParams_bottom.height=720;
        layoutParams_bottom.width=216;
        bottomBar.setLayoutParams(layoutParams_bottom);

        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!isRecording){
                    setupAudioRecorder();
                }
                else{
                    isRecording=false;
                    Log.d(TAG,"Recording is now stopped == "+isRecording);
                }
            }
        });
        //Add them to parent
        parentLinearLayout.addView(topBar);
        parentLinearLayout.addView(bottomBar);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(parentLinearLayout);
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
        if(surfaceTexture!=null){
            surfaceTexture.release();
        }
        releaseEGLSurface();
        releaseProgram();
        releaseEGLContext();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupCamera();
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            Log.d(TAG, "releaseCamera -- done");
        }
    }

    private void releaseEGLSurface(){
        EGL14.eglDestroySurface(mEGLDisplay,eglSurface);
    }

    private void releaseProgram(){
        GLES20.glDeleteProgram(mProgramHandle);
    }

    private void releaseEGLContext()
    {
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(mEGLDisplay);
        }

        mEGLDisplay = EGL14.EGL_NO_DISPLAY;
        mEGLContext = EGL14.EGL_NO_CONTEXT;
        mEGLConfig = null;
    }

    private void setupCamera()
    {
        Camera.CameraInfo info = new Camera.CameraInfo();
        for(int i=0;i<Camera.getNumberOfCameras();i++)
        {
            Camera.getCameraInfo(i, info);
            if(info.facing == Camera.CameraInfo.CAMERA_FACING_BACK){
                mCamera = Camera.open(i);
                cameraId = i;
                break;
            }
        }

        Camera.Parameters parameters = mCamera.getParameters();
        List<int[]> fps = parameters.getSupportedPreviewFpsRange();
        Iterator<int[]> iter = fps.iterator();
        //Safe to assume every camera would support 15 fps.
        int MIN_FPS = 15;
        int MAX_FPS = 15;
        while(iter.hasNext())
        {
            int[] frames = iter.next();
            if(!iter.hasNext())
            {
                MIN_FPS = frames[0];
                MAX_FPS = frames[1];
            }
        }
        Log.d(TAG,"Setting min and max Fps  == "+MIN_FPS+" , "+MAX_FPS);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        Log.d(TAG,"Width = "+metrics.widthPixels);
        Log.d(TAG,"Height = "+metrics.heightPixels);
        Log.d(TAG,"SCREEN Aspect Ratio = "+(double)metrics.widthPixels/(double)metrics.heightPixels);
        double screenAspectRatio = (double)metrics.widthPixels/(double)metrics.heightPixels;
        List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();

        //If none of the camera preview size will (closely) match with screen resolution, default it to take the first preview size value.
        VIDEO_HEIGHT = previewSizes.get(0).height;
        VIDEO_WIDTH = previewSizes.get(0).width;
        for(int i = 0;i<previewSizes.size();i++)
        {
            double ar = (double)previewSizes.get(i).width/(double)previewSizes.get(i).height;
            Log.d(TAG,"Aspect ratio for "+previewSizes.get(i).width+" / "+previewSizes.get(i).height+" is = "+ar);
            if(Math.abs(screenAspectRatio - ar) <= 0.2){
                //Best match for camera preview!!
                VIDEO_HEIGHT = previewSizes.get(i).height;
                VIDEO_WIDTH = previewSizes.get(i).width;
                break;
            }
        }
        Log.d(TAG,"HEIGTH == "+VIDEO_HEIGHT+", WIDTH == "+VIDEO_WIDTH);
        parameters.setPreviewSize(VIDEO_WIDTH,VIDEO_HEIGHT);
        parameters.setPreviewFpsRange(MIN_FPS,MAX_FPS);
        parameters.setRecordingHint(true);
        mCamera.setParameters(parameters);
    }

    private void showPreview()
    {
        Log.d(TAG, "starting camera preview");
        try {
            mCamera.setPreviewTexture(surfaceTexture);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        mCamera.startPreview();
    }

    private void setupVideoRecorder()
    {
        camcorderProfile = CamcorderProfile.get(cameraId,CamcorderProfile.QUALITY_HIGH);
        Log.d(TAG,"VID Bit rate = "+camcorderProfile.videoBitRate);
        Log.d(TAG,"VID videoFrameRate = "+camcorderProfile.videoFrameRate);
        Log.d(TAG,"VID videoFrameHeight = "+camcorderProfile.videoFrameHeight);
        Log.d(TAG,"VID videoFrameWidth = "+camcorderProfile.videoFrameWidth);
        Log.d(TAG,"AUD audioBitRate = "+camcorderProfile.audioBitRate);
        Log.d(TAG,"AUD audioChannels = "+camcorderProfile.audioChannels);
        Log.d(TAG,"AUD audioSampleRate = "+camcorderProfile.audioSampleRate);
        Log.d(TAG,"AUD audioCodec = "+camcorderProfile.audioCodec);
        videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE,VIDEO_WIDTH,VIDEO_HEIGHT);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, camcorderProfile.videoBitRate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, camcorderProfile.videoFrameRate);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        try {
            videoCodec = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
            videoCodec.configure(videoFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            videoSurface = videoCodec.createInputSurface();
            videoCodec.start();
            encoderSurface = prepareWindowSurface(videoSurface);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void releaseMuxerAndEncoder()
    {
        releaseAudioAndVideoCodecs();
        mediaMuxerHelper.stopMuxer();
        mediaMuxerHelper.releaseMuxer();
        Log.d(TAG,"MUXER RELEASED");
        //mediaMuxerHelper.releaseCodec();
        Log.d(TAG,"RELEASED ALL");
    }

    private void prepareMuxer()
    {
        File file = new File(getExternalFilesDir(null),"myaudio.mp4");
        Log.d(TAG,"Saving MEDIA at == "+file.getPath());
        try {
            mediaMuxer = new MediaMuxer(file.getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mediaMuxerHelper = new MediaMuxerHelper(mediaMuxer);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfCreated holder = " + surfaceHolder);
        prepareEGLDisplayandContext();
        eglSurface = prepareWindowSurface(surfaceHolder.getSurface());
        makeCurrent(eglSurface);

        mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT);

        muMVPMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uMVPMatrix");
        GlUtil.checkLocation(muMVPMatrixLoc, "uMVPMatrix");
        maPositionLoc = GLES20.glGetAttribLocation(mProgramHandle, "aPosition");
        GlUtil.checkLocation(maPositionLoc, "aPosition");
        maTextureCoordLoc = GLES20.glGetAttribLocation(mProgramHandle, "aTextureCoord");
        GlUtil.checkLocation(maTextureCoordLoc, "aTextureCoord");
        muMVPMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uMVPMatrix");
        GlUtil.checkLocation(muMVPMatrixLoc, "uMVPMatrix");
        muTexMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uTexMatrix");
        GlUtil.checkLocation(muTexMatrixLoc, "uTexMatrix");
        mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
        mTextureId = createGLTextureObject();
        surfaceTexture = new SurfaceTexture(mTextureId);
        surfaceTexture.setOnFrameAvailableListener(this);
        showPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged fmt=" + format + " size=" + width + "x" + height +
                " holder=" + holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceDestroyed holder=" + surfaceHolder);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            mainHandler.sendEmptyMessage(FRAME_AVAILABLE);
    }

    private void drawFrame()
    {
        if(mEGLConfig!=null) {
            makeCurrent(eglSurface);
            //Get next frame from camera
            surfaceTexture.updateTexImage();
            surfaceTexture.getTransformMatrix(mTmpMatrix);

            //Fill the surfaceview with Camera frame
            int viewWidth = cameraView.getWidth();
            int viewHeight = cameraView.getHeight();
            if (frameCount >= 0) {
                //Log.d(TAG, "FRAME Count = "+frameCount);
            }
            GLES20.glViewport(0, 0, viewWidth, viewHeight);
            draw(IDENTITY_MATRIX, createFloatBuffer(FULL_RECTANGLE_COORDS), 0, (FULL_RECTANGLE_COORDS.length / 2), 2, 2 * SIZEOF_FLOAT, mTmpMatrix,
                    createFloatBuffer(FULL_RECTANGLE_TEX_COORDS), mTextureId, 2 * SIZEOF_FLOAT);

            //Calls eglSwapBuffers.  Use this to "publish" the current frame.
            EGL14.eglSwapBuffers(mEGLDisplay, eglSurface);

            if (isRecording) {
                makeCurrent(encoderSurface);
                GLES20.glViewport(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);
                draw(IDENTITY_MATRIX, createFloatBuffer(FULL_RECTANGLE_COORDS), 0, (FULL_RECTANGLE_COORDS.length / 2), 2, 2 * SIZEOF_FLOAT, mTmpMatrix,
                        createFloatBuffer(FULL_RECTANGLE_TEX_COORDS), mTextureId, 2 * SIZEOF_FLOAT);
                recordHandler.sendEmptyMessage(FRAME_AVAILABLE);
                EGLExt.eglPresentationTimeANDROID(mEGLDisplay, encoderSurface, surfaceTexture.getTimestamp());
                EGL14.eglSwapBuffers(mEGLDisplay, encoderSurface);
            }
            frameCount++;
        }
    }
    /**
     * Allocates a direct float buffer, and populates it with the float array data.
     */
    private FloatBuffer createFloatBuffer(float[] coords) {
        // Allocate a direct ByteBuffer, using 4 bytes per float, and copy coords into it.
        ByteBuffer bb = ByteBuffer.allocateDirect(coords.length * SIZEOF_FLOAT);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(coords);
        fb.position(0);
        return fb;
    }

    private void makeCurrent(EGLSurface surface)
    {
        EGL14.eglMakeCurrent(mEGLDisplay, surface, surface, mEGLContext);
    }
    /**
     * Creates a texture object suitable for use with this program.
     * <p>
     * On exit, the texture will be bound.
     */
    public int createGLTextureObject() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GlUtil.checkGlError("glGenTextures");

        int texId = textures[0];
        GLES20.glBindTexture(mTextureTarget, texId);
        GlUtil.checkGlError("glBindTexture " + texId);

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GlUtil.checkGlError("glTexParameter");

        return texId;
    }

    private void prepareEGLDisplayandContext()
    {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            mEGLDisplay = null;
            throw new RuntimeException("unable to initialize EGL14");
        }
        EGLConfig config = getConfig(FLAG_RECORDABLE, 2);
        if (config == null) {
            throw new RuntimeException("Unable to find a suitable EGLConfig");
        }
        int[] attrib2_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        EGLContext context = EGL14.eglCreateContext(mEGLDisplay, config, EGL14.EGL_NO_CONTEXT,
                attrib2_list, 0);
        checkEglError("eglCreateContext");
        mEGLConfig = config;
        mEGLContext = context;

        // Confirm with query.
        int[] values = new int[1];
        EGL14.eglQueryContext(mEGLDisplay, mEGLContext, EGL14.EGL_CONTEXT_CLIENT_VERSION,
                values, 0);
        Log.d(TAG, "EGLContext created, client version " + values[0]);
    }

    private void checkEglError(String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }

    private EGLSurface prepareWindowSurface(Surface surface)
    {
        // Create a window surface, and attach it to the Surface we received.
        int[] surfaceAttribs = {
                EGL14.EGL_NONE
        };
        eglSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, mEGLConfig,surface ,
                surfaceAttribs, 0);
        checkEglError("eglCreateWindowSurface");
        if (eglSurface == null) {
            throw new RuntimeException("surface was null");
        }
        return eglSurface;
    }

    /**
     * Issues the draw call.  Does the full setup on every call.
     *
     * @param mvpMatrix The 4x4 projection matrix.
     * @param vertexBuffer Buffer with vertex position data.
     * @param firstVertex Index of first vertex to use in vertexBuffer.
     * @param vertexCount Number of vertices in vertexBuffer.
     * @param coordsPerVertex The number of coordinates per vertex (e.g. x,y is 2).
     * @param vertexStride Width, in bytes, of the position data for each vertex (often
     *        vertexCount * sizeof(float)).
     * @param texMatrix A 4x4 transformation matrix for texture coords.  (Primarily intended
     *        for use with SurfaceTexture.)
     * @param texBuffer Buffer with vertex texture data.
     * @param texStride Width, in bytes, of the texture data for each vertex.
     */
    private void draw(float[] mvpMatrix, FloatBuffer vertexBuffer, int firstVertex,
                     int vertexCount, int coordsPerVertex, int vertexStride,
                     float[] texMatrix, FloatBuffer texBuffer, int textureId, int texStride) {
        GlUtil.checkGlError("draw start");

        // Select the program.
        GLES20.glUseProgram(mProgramHandle);
        GlUtil.checkGlError("glUseProgram");

        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(mTextureTarget, textureId);

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mvpMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");

        // Copy the texture transformation matrix over.
        GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, texMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionLoc);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(maPositionLoc, coordsPerVertex,
                GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);
        GlUtil.checkGlError("glVertexAttribPointer");

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(maTextureCoordLoc, 2,
                GLES20.GL_FLOAT, false, texStride, texBuffer);
        GlUtil.checkGlError("glVertexAttribPointer");

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, firstVertex, vertexCount);
        GlUtil.checkGlError("glDrawArrays");

        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionLoc);
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc);
        GLES20.glBindTexture(mTextureTarget, 0);
        GLES20.glUseProgram(0);
    }

    private EGLConfig getConfig(int flags, int version) {
        int renderableType = EGL14.EGL_OPENGL_ES2_BIT;

        // The actual surface is generally RGBA or RGBX, so situationally omitting alpha
        // doesn't really help.  It can also lead to a huge performance hit on glReadPixels()
        // when reading into a GL_RGBA buffer.
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                //EGL14.EGL_DEPTH_SIZE, 16,
                //EGL14.EGL_STENCIL_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, renderableType,
                EGL14.EGL_NONE, 0,      // placeholder for recordable [@-3]
                EGL14.EGL_NONE
        };
        if ((flags & FLAG_RECORDABLE) != 0) {
            attribList[attribList.length - 3] = EGL_RECORDABLE_ANDROID;
            attribList[attribList.length - 2] = 1;
        }
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length,
                numConfigs, 0)) {
            Log.w(TAG, "unable to find RGB8888 / " + version + " EGLConfig");
            return null;
        }
        return configs[0];
    }

    private void setupAudioRecorder()
    {
        try {
            audioFormat = MediaFormat.createAudioFormat(AudioVideoRecording.MIME_TYPE, AudioVideoRecording.SAMPLE_RATE, 1);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE,AudioVideoRecording.BIT_RATE);
            audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
            mediaCodec = MediaCodec.createEncoderByType(AudioVideoRecording.MIME_TYPE);
            mediaCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();
            isRecording=true;

            int min_buffer_size = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            Log.d(TAG,"MIN Buffer size == "+min_buffer_size);
            int buffer_size = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER;
            if (buffer_size < min_buffer_size)
                buffer_size = ((min_buffer_size / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;
            Log.d(TAG,"Buffer size == "+buffer_size);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT, buffer_size);

            Log.d(TAG,"Audio record state == "+audioRecord.getState());
            if(audioRecord.getState() == 0)
            {
                final int[] AUDIO_SOURCES = new int[] {
                        MediaRecorder.AudioSource.DEFAULT,
                        MediaRecorder.AudioSource.CAMCORDER,
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                };
                for(int audioSource : AUDIO_SOURCES)
                {
                    audioRecord = new AudioRecord(audioSource,SAMPLE_RATE ,
                            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer_size);
                    if(audioRecord.getState() == 1)
                    {
                        Log.d(TAG,"audioSource == "+audioSource);
                        break;
                    }
                    audioRecord=null;
                }
            }
            if(audioRecord == null || audioRecord.getState() == 0){
                Toast.makeText(getApplicationContext(),"Audio record not supported in this device.",Toast.LENGTH_SHORT).show();
                mediaCodec.stop();
                mediaCodec.release();
                return;
            }
            Log.d(TAG,"Recording is now started == "+isRecording);
            Toast.makeText(getApplicationContext(),"Audio Record STARTED",Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void waitUntilReady()
    {
        synchronized (recordSync){
            try {
                Log.d(TAG,"Waiting...");
                recordSync.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Log.d(TAG,"Come out of WAIT");
    }

    class RecordVideo extends Thread
    {
        boolean isEOS=false;
        long count=0;
        int trackIndex = 0;

        public VideoRecordHandler getHandle(){
            VideoRecordHandler videoRecordHandler=new VideoRecordHandler(this);
            return videoRecordHandler;
        }
        @Override
        public void run(){
            Looper.prepare();
            recordHandler = getHandle();
            synchronized (recordSync){
                recordSync.notify();
            }
            Looper.loop();
            Log.d(TAG,"End of Loop");
        }

        void shutdown()
        {
            Looper.myLooper().quit();
            EGL14.eglDestroySurface(mEGLDisplay,encoderSurface);
            releaseMuxerAndEncoder();
            videoSurface.release();
            /*EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);*/
        }

        void recordStop()
        {
            Log.d(TAG,"Drain one last time");
            drain();
            videoCodec.signalEndOfInputStream();
            countBytes();
        }

        private void setupVideoRecorder()
        {
            camcorderProfile = CamcorderProfile.get(cameraId,CamcorderProfile.QUALITY_HIGH);
            Log.d(TAG,"VID Bit rate = "+camcorderProfile.videoBitRate);
            Log.d(TAG,"VID videoFrameRate = "+camcorderProfile.videoFrameRate);
            Log.d(TAG,"VID videoFrameHeight = "+camcorderProfile.videoFrameHeight);
            Log.d(TAG,"VID videoFrameWidth = "+camcorderProfile.videoFrameWidth);
            Log.d(TAG,"AUD audioBitRate = "+camcorderProfile.audioBitRate);
            Log.d(TAG,"AUD audioChannels = "+camcorderProfile.audioChannels);
            Log.d(TAG,"AUD audioSampleRate = "+camcorderProfile.audioSampleRate);
            Log.d(TAG,"AUD audioCodec = "+camcorderProfile.audioCodec);
            videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE,VIDEO_WIDTH,VIDEO_HEIGHT);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, camcorderProfile.videoBitRate);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, camcorderProfile.videoFrameRate);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
            try {
                videoCodec = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
                videoCodec.configure(videoFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
                videoSurface = videoCodec.createInputSurface();
                videoCodec.start();
                encoderSurface = prepareWindowSurface(videoSurface);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void sendToMuxer()
        {
            /*if(!isRecording){
                Log.d(TAG, "send Video BUFFER_FLAG_END_OF_STREAM");
                //videoCodec.signalEndOfInputStream();
                isEOS=true;
            }*/
            try {
                /*while(true) {
                    if(!isEOS) {

                    }
                    else {

                        break;
                    }
                    synchronized (recordSync) {
                        isCapturing=false;
                        if (!isCapturing) {
                            Log.d(TAG,"Waiting......");
                            try {
                                recordSync.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }*/
                drain();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }

        private void drain()
        {
            int videoBufferInd;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            //This loop will process one frame of data from Camera.
            ByteBuffer[] outputBuffers = videoCodec.getOutputBuffers();
            while (true) {
                //Extract encoded data
                Log.d(TAG, "Retrieve Encoded Data....");
                videoBufferInd = videoCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT);
                if((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0){
                    Log.d(TAG,"EOS Reached...");
                    break;
                }
                Log.d(TAG, "OUTPUT buffer index = " + videoBufferInd);
                if (videoBufferInd >= 0) {
                    if (bufferInfo.size != 0) {
                        outputBuffers[videoBufferInd].position(bufferInfo.offset);
                        bufferInfo.presentationTimeUs=System.nanoTime()/1000;
                        outputBuffers[videoBufferInd].limit(bufferInfo.offset + bufferInfo.size);
                        Log.d(TAG, "Writing data size == " + bufferInfo.size);
                        count += bufferInfo.size;
                        mediaMuxerHelper.recordMedia(videoCodec, bufferInfo, false, trackIndex, outputBuffers[videoBufferInd], videoBufferInd);
                        videoCodec.releaseOutputBuffer(videoBufferInd,false);
                                /*if (isEOS || !isRecording) {
                                    break MAIN_LOOP;
                                }*/
                    }
                } else if (videoBufferInd == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = videoCodec.getOutputBuffers();
                } else if (videoBufferInd == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Subsequent data will conform to new format.
                    videoFormat = videoCodec.getOutputFormat();
                    trackIndex = mediaMuxerHelper.addTrack(videoFormat, false);
                    isVideoAdded = true;
                    mediaMuxerHelper.startMuxer();
                } else if (videoBufferInd == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        Log.d(TAG,"Coming out since no more frame data to read");
                        break;
                    }
                }
            }

        private void setEOS(boolean eos)
        {
            isEOS = eos;
        }

        private void countBytes()
        {
            String bytes = "";
            if (count > 1000000){
                bytes = count/1000000+" MB";
            }
            else if(count > 1000){
                bytes = count/1000+" KB";
            }
            else{
                bytes = count+" Bytes";
            }
            Log.d(TAG,"Written "+bytes+" of data");
            Log.d(TAG,"Video Record STOPPED");
            Log.d(TAG,"Video saved");
            isVideoAdded=false;
        }

        class VideoRecordHandler extends Handler
        {
            WeakReference<RecordVideo> recordVid;
            public VideoRecordHandler(RecordVideo recordVideo) {
                recordVid = new WeakReference<>(recordVideo);
            }

            @Override
            public void handleMessage(Message msg) {
                RecordVideo recordVideo = recordVid.get();
                switch(msg.what)
                {
                    case FRAME_AVAILABLE:
                        Log.d(TAG, "send FRAME DATA");
                        /*synchronized (recordSync) {
                            //isRecording=true;
                            isCapturing = true;
                            recordSync.notify();
                        }*/
                        recordVideo.sendToMuxer();
                        break;
                    case RECORD_STOP:
                        Log.d(TAG, "send Video BUFFER_FLAG_END_OF_STREAM");
                        recordVideo.setEOS(true);
                        recordVideo.recordStop();
                        recordVideo.shutdown();
                        /*while(isCapturing){

                        }*/
                        /*synchronized (recordSync) {
                            isCapturing = true;
                            //isRecording=false;
                            recordSync.notify();
                        }*/
                        break;
                    case RECORD_START:
                        recordVideo.setupVideoRecorder();
                        isRecording=true;
                        break;
                    /*case GET_RECORDER:
                        video = recordVideo;
                        break;*/
                    /*case SHUTDOWN:
                        recordVideo.shutdown();
                        break;*/
                    /*case GET_READY:
                        recordVideo.setEOS(false);
                        recordVideo.start();
                        break;*/
                }
            }
        }
    }

    class RecordAudio extends Thread
    {
        @Override
        public void run() {
            int len = 0, bufferIndex = 0;
            audioRecord.startRecording();
            final ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            Log.d(TAG,"Input buffer length == "+inputBuffers.length);
            ByteBuffer buf;
            boolean isEOS;
            int trackIndex = 0;
            int audioBufferInd;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            long count=0;
            try {

MAIN_LOOP:                while (true) {
                    bufferIndex = mediaCodec.dequeueInputBuffer(TIMEOUT);
                    isEOS=false;
                    //Log.d(TAG,"INPUT buffer Index == "+bufferIndex);
                    if(!isRecording){
                        Log.d(TAG, "send BUFFER_FLAG_END_OF_STREAM");
                        mediaCodec.queueInputBuffer(bufferIndex, 0, len, System.nanoTime()/1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS=true;
                    }

                    if(bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER){
                        //Do nothing. Need to wait till encoder is ready to accept data again.
                    }
                    else if(bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                        //Log.d(TAG,"Output format changed");
                        audioFormat = mediaCodec.getOutputFormat();
                    }

                    if (bufferIndex>=0 && !isEOS) {
                        buf = inputBuffers[bufferIndex];
                        buf.clear();
                        len = audioRecord.read(buf,SAMPLES_PER_FRAME);
                        if (len ==  AudioRecord.ERROR_INVALID_OPERATION || len == AudioRecord.ERROR_BAD_VALUE) {
                            Log.e(this.getClass().getName(),"An error occurred with the AudioRecord API !");
                        } else {
                            //Log.d(TAG,"Pushing raw audio to the decoder: len="+len+" bs: "+inputBuffers[bufferIndex].capacity());
                            mediaCodec.queueInputBuffer(bufferIndex, 0, len, System.nanoTime()/1000, 0);
                        }
                    }

                    //Extract encoded data
                        ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
INNER_LOOP:             while(true) {
                            //Log.d(TAG, "Retrieve Encoded Data....");
                            audioBufferInd = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT);
                            //Log.d(TAG, "OUTPUT buffer index = " + audioBufferInd);
                            if (audioBufferInd >= 0) {
                                if (bufferInfo.size != 0) {
                                    outputBuffers[audioBufferInd].position(bufferInfo.offset);
                                    bufferInfo.presentationTimeUs=System.nanoTime()/1000;
                                    outputBuffers[audioBufferInd].limit(bufferInfo.offset + bufferInfo.size);
                                    //Log.d(TAG, "Writing data size == " + bufferInfo.size);
                                    count+=bufferInfo.size;
                                    mediaMuxerHelper.recordMedia(mediaCodec,bufferInfo,true,trackIndex,outputBuffers[audioBufferInd],audioBufferInd);
                                    //mediaMuxer.writeSampleData(trackIndex, outputBuffers[audioBufferInd], bufferInfo);
                                    //mediaCodec.releaseOutputBuffer(audioBufferInd, false);
                                    if(!isEOS) {
                                        break INNER_LOOP;
                                    }
                                    else{
                                        break MAIN_LOOP;
                                    }
                                }
                            } else if (audioBufferInd == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                outputBuffers = mediaCodec.getOutputBuffers();
                            } else if (audioBufferInd == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                // Subsequent data will conform to new format.
                                audioFormat = mediaCodec.getOutputFormat();
                                //trackIndex = mediaMuxer.addTrack(audioFormat);
                                //mediaMuxer.start();
                                trackIndex = mediaMuxerHelper.addTrack(audioFormat,true);
                                isAudioAdded=true;
                                while(!isVideoAdded){
                                    Thread.sleep(10);
                                    Log.d(TAG,"Audio Sleeping");
                                }
                                mediaMuxerHelper.startMuxer();
                            } else if (audioBufferInd == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                if (!isEOS) {
                                    break INNER_LOOP;
                                }
                                else{
                                    break MAIN_LOOP;
                                }
                            }
                    }
                }
                String bytes = "";
                if (count > 1000000){
                    bytes = count/1000000+" MB";
                }
                else if(count > 1000){
                    bytes = count/1000+" KB";
                }
                else{
                    bytes = count+" Bytes";
                }
                Log.d(TAG,"Written "+bytes+" of data");
                audioRecord.stop();
                Log.d(TAG,"Audio Record STOPPED");
                Log.d(TAG,"Audio saved");
                isAudioAdded=false;
            } catch (RuntimeException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                if (audioRecord != null) {
                    audioRecord.release();
                }
            }
        }
    }

    private void releaseAudioAndVideoCodecs()
    {
        /*if(mediaCodec!=null){
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec=null;
        }*/
        Log.d(TAG,"RELEASE START");
        try {
            if (videoCodec != null) {
                videoCodec.stop();
                videoCodec.release();
                videoCodec = null;
            }
        }catch(Exception e){
            Log.e(TAG,e.getMessage());
        }
        Log.d(TAG,"RELEASE DONE");
    }

    class MediaMuxerHelper
    {
        MediaMuxer mediaMuxer;
        boolean muxerStarted=false;

        public MediaMuxerHelper(MediaMuxer mMuxer)
        {
            mediaMuxer = mMuxer;
        }

        synchronized public void recordMedia(MediaCodec mediaCodec, MediaCodec.BufferInfo bufferInfo, boolean audioTrack,int trackIndex,ByteBuffer mediaData,int audioBufferInd)
        {
            //Extract encoded data
            Log.d(TAG,"Recording for "+(audioTrack ? "AUDIO" : "VIDEO"));
            mediaMuxer.writeSampleData(trackIndex, mediaData, bufferInfo);
        }

        synchronized public int addTrack(MediaFormat mediaFormat,boolean audioTrack)
        {
            Log.d(TAG,"adding track for "+(audioTrack ? "AUDIO" : "VIDEO"));
            return mediaMuxer.addTrack(mediaFormat);
        }

        private void releaseMuxer()
        {
            if(mediaMuxer!=null) {
                mediaMuxer.release();
                mediaMuxer=null;
            }
        }

        synchronized public void startMuxer()
        {
            if(mediaMuxer!=null) {
                if (!muxerStarted) {
                    mediaMuxer.start();
                    muxerStarted=true;
                }
            }
        }

        private void stopMuxer()
        {
            if(mediaMuxer!=null){
                if(muxerStarted){
                    mediaMuxer.stop();
                    muxerStarted=false;
                }
            }
        }

        private void releaseCodec()
        {
            if(mediaCodec!=null){
                mediaCodec.stop();
                mediaCodec.release();
                mediaCodec=null;
            }
        }
    }

    class MainHandler extends Handler {
        WeakReference<AudioVideoRecording> recordVid;

        public MainHandler(AudioVideoRecording recordVideo) {
            recordVid = new WeakReference<>(recordVideo);
        }

        @Override
        public void handleMessage(Message msg) {

            AudioVideoRecording audioVideoRecording = recordVid.get();
            switch(msg.what)
            {
                case FRAME_AVAILABLE:
                    audioVideoRecording.drawFrame();
                    break;
            }
        }
    }
}
