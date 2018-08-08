package com.hhu.yannick.coinclassificator.AsyncProcessor.Processor;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class ResizeGP extends GraphicsProcessor {
    private Type type;
    public enum Type{
        LINEAR,

        RESIZE_ELLIPSE
    }

    public ResizeGP(Type type){
        this.type = type;
        task = type.toString() + "_Resize";

        parameter.put("width", 256);
        parameter.put("height", -1);
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

        Mat resized = new Mat();

        float scale = mat.height() / (float)mat.width();
        float width = getInt("width") > 0 ? getInt("width") : getInt("height") / scale;
        float height = getInt("height") > 0 ? getInt("height") : getInt("width") * scale;

        if(type == Type.LINEAR) {
            Imgproc.resize(mat, resized, new Size(width, height), 0, 0, Imgproc.INTER_LINEAR);
            data.put("mat", resized);
        }
        else if(type == Type.RESIZE_ELLIPSE){
            List<RotatedRect> ellipses = (ArrayList<RotatedRect>)data.get("ellipses");

            // scale ellipses
            for (int i = 0; i < ellipses.size(); i++) {
                RotatedRect rect = ellipses.get(i);
                rect = new RotatedRect(new Point(rect.center.x * (mat.width() / width), rect.center.y * (mat.height() / height)),
                        new Size(rect.size.width * (mat.width() / width), rect.size.height * (mat.height() / height)), rect.angle);

                ellipses.remove(i);
                ellipses.add(i, rect);
            }
            data.put("ellipses", ellipses);
        }

        return Status.PASSED;
    }
}
