package com.dual_camera_video_mock;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioActivity extends Activity{

    private ImageButton mRecordButton;
    private AudioRecord audioRecord;
    private MediaCodec mediaCodec;
    private static final String TAG = AudioActivity.class.getName();
    private volatile boolean isRecording=false;
    final static String MIME_TYPE = "audio/mp4a-latm";
    final static int SAMPLE_RATE = 44100;
    final static int BIT_RATE = 128000;
    public static final int SAMPLES_PER_FRAME = 1024;	// AAC, bytes/frame/channel
    public static final int FRAMES_PER_BUFFER = 25; 	// AAC, frame/buffer/sec
    MediaFormat format=null;
    int TIMEOUT = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio);
        mRecordButton = (ImageButton)findViewById(R.id.record_button);
        mRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isRecording)
                    startRecording();
                else
                    stopRecording();
            }
        });
    }

    private void startRecording() {
        Log.v(TAG, "startRecording:");
        setupAudioRecorder();
    }

    private void stopRecording() {
        isRecording=false;
    }

    private void setupAudioRecorder()
    {
        try {
            format = MediaFormat.createAudioFormat(AudioActivity.MIME_TYPE, AudioActivity.SAMPLE_RATE, 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE,AudioActivity.BIT_RATE);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            format.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
            mediaCodec = MediaCodec.createEncoderByType(AudioActivity.MIME_TYPE);
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
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
                    //Log.d(TAG,"Audio record state 2222 == "+audioRecord.getState());
                    if(audioRecord.getState() == 1)
                    {
                        Log.d(TAG,"audioSource == "+audioSource);
                        break;
                    }
                    audioRecord=null;
                }
            }
            if(audioRecord == null || audioRecord.getState() == 0){
                Toast.makeText(getApplication(),"Audio record not supported in this device.",Toast.LENGTH_SHORT).show();
                mediaCodec.stop();
                mediaCodec.release();
                return;
            }
            Log.d(TAG,"Recording is now started == "+isRecording);
            Toast.makeText(getApplication(),"Audio Record STARTED",Toast.LENGTH_SHORT).show();
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
            final ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            Log.d(TAG,"Input buffer length == "+inputBuffers.length);
            MediaMuxer mediaMuxer = null;
            ByteBuffer buf;
            boolean isEOS;
            int trackIndex = 0;
            int audioBufferInd;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            long count=0;
            try {
                File file = new File(getExternalFilesDir(null),"myaudio.m4a");
                Log.d(TAG,"Saving audio at == "+file.getPath());
                mediaMuxer = new MediaMuxer(file.getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

                MAIN_LOOP:                while (true) {
                    bufferIndex = mediaCodec.dequeueInputBuffer(TIMEOUT);
                    isEOS=false;
                    Log.d(TAG,"INPUT buffer Index == "+bufferIndex);
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
                            //Log.d(TAG,"Pushing raw audio to the decoder: len="+len+" bs: "+inputBuffers[bufferIndex].capacity());
                            mediaCodec.queueInputBuffer(bufferIndex, 0, len, System.nanoTime()/1000, 0);
                        }
                    }

                    //Extract encoded data
                    ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
                    INNER_LOOP:             while(true) {
                        Log.d(TAG, "Retrieve Encoded Data....");
                        audioBufferInd = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT);
                        Log.d(TAG, "OUTPUT buffer index = " + audioBufferInd);
                        if (audioBufferInd >= 0) {
                            if (bufferInfo.size != 0) {
                                outputBuffers[audioBufferInd].position(bufferInfo.offset);
                                bufferInfo.presentationTimeUs=System.nanoTime()/1000;
                                outputBuffers[audioBufferInd].limit(bufferInfo.offset + bufferInfo.size);
                                Log.d(TAG, "Writing data size == " + bufferInfo.size);
                                count+=bufferInfo.size;
                                mediaMuxer.writeSampleData(trackIndex, outputBuffers[audioBufferInd], bufferInfo);
                                mediaCodec.releaseOutputBuffer(audioBufferInd, false);
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
                            format = mediaCodec.getOutputFormat();
                            trackIndex = mediaMuxer.addTrack(format);
                            mediaMuxer.start();
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
                Log.d(TAG,"TOTAL "+count+" of data");
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