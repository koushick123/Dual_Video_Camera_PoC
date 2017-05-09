package com.dual_camera_video_mock;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioVideoRecording extends AppCompatActivity {

    private MyGLSurfaceView cameraView;
    private int MY_PERMISSIONS_REQUEST_CAMERA=0;
    private AudioRecord audioRecord;
    private MediaCodec mediaCodec;
    private volatile boolean isRecording=false;
    int bufferSize=32;
    final static String MIME_TYPE = "audio/mp4a-latm";
    final static int SAMPLE_RATE = 44100;
    final static int BIT_RATE = 128000;
    public static final int SAMPLES_PER_FRAME = 1024;	// AAC, bytes/frame/channel
    public static final int FRAMES_PER_BUFFER = 25; 	// AAC, frame/buffer/sec
    MediaFormat format;
    int TIMEOUT = 10000;
    private final String TAG = this.getClass().getName();

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == MY_PERMISSIONS_REQUEST_CAMERA){
            setupCameraPreview();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int permission = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA);
        if(permission == PackageManager.PERMISSION_GRANTED) {
            setupCameraPreview();
        }
        else{
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    MY_PERMISSIONS_REQUEST_CAMERA);
        }
    }

    private void setupCameraPreview()
    {
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
        parentLinearLayout.addView(cameraView);
        parentLinearLayout.addView(bottomBar);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(parentLinearLayout);
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

    private void setupAudioRecorder()
    {
        final int min_buffer_size = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        int buffer_size = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER;
        if (buffer_size < min_buffer_size)
            buffer_size = ((min_buffer_size / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;
        Log.d(TAG,"Buffer size == "+buffer_size);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,AudioVideoRecording.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT, buffer_size);
        try {
            format = MediaFormat.createAudioFormat(AudioVideoRecording.MIME_TYPE,AudioVideoRecording.SAMPLE_RATE,1);
            format.setInteger(MediaFormat.KEY_BIT_RATE,AudioVideoRecording.BIT_RATE);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            format.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
            mediaCodec = MediaCodec.createEncoderByType(AudioVideoRecording.MIME_TYPE);
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            isRecording=true;
            Log.d(TAG,"Recording is now started == "+isRecording);
            Toast.makeText(getApplicationContext(),"Audio Record STARTED",Toast.LENGTH_SHORT).show();
            try {
                new Thread(new RecordAudio()).start();
            }
            catch (Exception e){
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class RecordAudio extends Thread
    {
        @Override
        public void run() {
            int len = 0, bufferIndex = 0;
            audioRecord.startRecording();
            mediaCodec.start();
            final ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            Log.d(TAG,"Input buffer length == "+inputBuffers.length);
            MediaMuxer mediaMuxer = null;
            ByteBuffer buf;
            boolean isEOS=false;
            int trackIndex = 0;
            int audioBufferInd;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int count=1;
            try {
                File file = new File(getExternalFilesDir(null),"myaudio.mp4");
                Log.d(TAG,"Saving audio at == "+file.getPath());
                mediaMuxer = new MediaMuxer(file.getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

                while (true) {
                    bufferIndex = mediaCodec.dequeueInputBuffer(TIMEOUT);
                    isEOS=false;
                    Log.d(TAG,"Index == "+bufferIndex);
                    if(!isRecording){
                        Log.d(TAG, "send BUFFER_FLAG_END_OF_STREAM");
                        mediaCodec.queueInputBuffer(bufferIndex, 0, len, System.nanoTime()/1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS=true;
                    }

                    if(bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER){
                        //Do nothing. Need to wait till encoder is ready to accept data again.
                    }
                    else if(bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                        Log.d(TAG,"Output format changed");
                        format = mediaCodec.getOutputFormat();
                    }

                    if (bufferIndex>=0 && !isEOS) {
                        buf = inputBuffers[bufferIndex];
                        buf.clear();
                        len = audioRecord.read(buf,SAMPLES_PER_FRAME);
                        if (len ==  AudioRecord.ERROR_INVALID_OPERATION || len == AudioRecord.ERROR_BAD_VALUE) {
                            Log.e(this.getClass().getName(),"An error occurred with the AudioRecord API !");
                        } else {
                            Log.d(TAG,"Pushing raw audio to the decoder: len="+len+" bs: "+inputBuffers[bufferIndex].capacity());
                            mediaCodec.queueInputBuffer(bufferIndex, 0, len, System.nanoTime()/1000, 0);
                        }
                    }

                    //Extract encoded data
                    Log.d(TAG,"Retrieve Encoded Data....");
                    ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
                    audioBufferInd = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT);
                    Log.d(TAG,"Audio buffer index = "+audioBufferInd);
                    if (audioBufferInd >= 0) {
                        if (bufferInfo.size != 0) {
                            outputBuffers[audioBufferInd].position(bufferInfo.offset);
                            outputBuffers[audioBufferInd].limit(bufferInfo.offset + bufferInfo.size);
                            Log.d(TAG,"Writing data size == "+bufferInfo.size);
                            mediaMuxer.writeSampleData(trackIndex, outputBuffers[audioBufferInd], bufferInfo);
                            mediaCodec.releaseOutputBuffer(audioBufferInd,false);
                        }
                    } else if (audioBufferInd == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        outputBuffers = mediaCodec.getOutputBuffers();
                    } else if (audioBufferInd == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // Subsequent data will conform to new format.
                        format = mediaCodec.getOutputFormat();
                        trackIndex = mediaMuxer.addTrack(format);
                        mediaMuxer.start();
                    }
                    else if (audioBufferInd == MediaCodec.BUFFER_FLAG_END_OF_STREAM){
                        break;
                    }
                    else if (audioBufferInd == MediaCodec.INFO_TRY_AGAIN_LATER){
                        if(!isEOS){
                            count++;
                            if(count > 100){
                                break;
                            }
                        }
                    }
                }
                audioRecord.stop();
                Log.d(TAG,"Audio Record STOPPED");
                Log.d(TAG,"Audio saved");
            } catch (RuntimeException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                if(audioRecord!=null){
                    audioRecord.release();
                }
                if(mediaMuxer!=null) {
                    mediaMuxer.stop();
                    mediaMuxer.release();
                }
                if(mediaCodec!=null){
                    mediaCodec.stop();
                    mediaCodec.release();
                }
                Log.d(TAG,"RELEASED ALL");
            }
        }
    }
}
