package com.dual_camera_video_mock;

import android.media.MediaCodec;

import java.nio.ByteBuffer;

/**
 * Created by Koushick on 02-06-2017.
 */
public class FrameData {

    MediaCodec.BufferInfo bufferInfo;
    ByteBuffer byteBuffer;

    public FrameData(MediaCodec.BufferInfo bufferInfo, ByteBuffer byteBuffer) {
        this.bufferInfo = bufferInfo;
        this.byteBuffer = byteBuffer;
    }

    public MediaCodec.BufferInfo getBufferInfo() {
        return bufferInfo;
    }

    public void setBufferInfo(MediaCodec.BufferInfo bufferInfo) {
        this.bufferInfo = bufferInfo;
    }

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    public void setByteBuffer(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }
}
