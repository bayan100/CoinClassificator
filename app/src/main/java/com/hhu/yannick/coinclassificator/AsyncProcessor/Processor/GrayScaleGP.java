package com.hhu.yannick.coinclassificator.AsyncProcessor.Processor;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class GrayScaleGP extends GraphicsProcessor {
    public GrayScaleGP() {
    }

    @Override
    protected Status executeProcess() {
        Mat mat = null;
        if (data.containsKey("mat"))
            mat = (Mat)data.get("mat");
        else if(data.containsKey("bitmap"))
            mat = toMat((Bitmap)data.get("bitmap"));
        else
            return Status.FAILED;

        Mat dest = new Mat();
        Imgproc.cvtColor(mat, dest, Imgproc.COLOR_RGB2GRAY);

        data.put("mat", dest);

        // DEBUG
        Log.d("GRAY", "gray");
        data.put("bitmap", toBitmap(dest));
        return Status.PASSED;
    }
}
