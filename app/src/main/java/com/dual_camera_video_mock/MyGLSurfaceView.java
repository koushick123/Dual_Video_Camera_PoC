package com.dual_camera_video_mock;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.SurfaceHolder;

/**
 * Created by Koushick on 01-05-2017.
 */
class MyGLSurfaceView extends GLSurfaceView {

    private final MyGLRenderer mRenderer;
    private String TAG = "MyGLSurfaceView";

    public MyGLSurfaceView(Context context){
        super(context);

        // Create an OpenGL ES 2.0 context
        setEGLContextClientVersion(2);

        mRenderer = new MyGLRenderer(this);

        // Set the Renderer for drawing on the GLSurfaceView
        setRenderer(mRenderer);
    }

    public void closeCamera()
    {
        Log.d(TAG,"closing camera");
        mRenderer.close();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        super.surfaceCreated(holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
        Log.d(TAG,"surfaceDestroyed");
        mRenderer.close();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        super.surfaceChanged(holder, format, w, h);
    }
}