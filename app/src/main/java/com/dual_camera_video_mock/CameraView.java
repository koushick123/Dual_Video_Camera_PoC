package com.dual_camera_video_mock;

import android.content.Context;
import android.graphics.Canvas;
import android.view.SurfaceView;

/**
 * Created by Koushick on 06-06-2017.
 */
public class CameraView extends SurfaceView {
    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public CameraView(Context context) {
        super(context);
    }
}
