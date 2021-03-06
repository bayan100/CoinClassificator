package com.hhu.yannick.coinclassificator.AsyncProcessor.Processor;

import android.graphics.Bitmap;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class BlurGP extends GraphicsProcessor {

    private Type type;
    public enum Type{
        MEDIAN,
        GAUSS
    }

    public BlurGP(Type type){
        this.type = type;
        task = type.toString() + "_Blur";
        parameter.put("kSize", 7);
        parameter.put("sigma", 1f);
    }

    @Override
    protected Status executeProcess() {

        Mat material = null;
        if (data.containsKey("mat"))
            material = (Mat) data.get("mat");
        else if (data.containsKey("bitmap"))
            material = toMat((Bitmap) data.get("bitmap"));
        else
            return Status.FAILED;

        if(type == Type.MEDIAN) {
            int ksize = getInt("kSize");
            Imgproc.medianBlur(material, material, ksize);
            data.put("mat", material);
        }
        else if(type == Type.GAUSS){
            int ksize = getInt("kSize");
            float sigma = getFloat("sigma");
            Imgproc.GaussianBlur(material, material, new Size(ksize, ksize), sigma);
            data.put("mat", material);
        }
        return Status.PASSED;
    }
}
