package com.hhu.yannick.coinclassificator.AsyncProcessor.Processor;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

import static org.opencv.core.CvType.CV_8U;
import static org.opencv.core.CvType.CV_8UC1;

public class ResizeGP extends GraphicsProcessor {
    private Type type;
    public enum Type{
        LINEAR,

        CROP,
        RESIZE_ELLIPSE
    }

    public ResizeGP(Type type){
        this.type = type;
        task = type.toString() + "_Resize";

        parameter.put("width", 256);
        parameter.put("height", -1);
        parameter.put("backColor", 0);
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

        // if we have exact dimensions
        if(getInt("width") > 0 && getInt("height") > 0){
            width = getInt("width");
            height = getInt("height");
        }

        switch (type)
        {
            case LINEAR:
                Imgproc.resize(mat, resized, new Size(width, height), 0, 0, Imgproc.INTER_LINEAR);
                data.put("mat", resized);
                data.put("bitmap", toBitmap(resized));
                break;

            case RESIZE_ELLIPSE:
                if(!data.containsKey("ellipses"))
                    return Status.FAILED;
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
                break;

            case CROP:
                if(!data.containsKey("ellipses"))
                    return Status.FAILED;
                ellipses = (ArrayList<RotatedRect>)data.get("ellipses");
                if(ellipses.size() < 1)
                    return Status.FAILED;
                RotatedRect ellipse = ellipses.get(0);

                int cInt = getInt("backColor");
                Scalar color = new Scalar(cInt, cInt, cInt);

                // put a mask over the non selected Area
                Mat mask = Mat.zeros(mat.size(), CV_8U);
                Imgproc.ellipse(mask, ellipse, new Scalar(1,1,1), -1);

                Mat out = new Mat(mat.size(), mat.type());
                out.setTo(new Scalar(0, 0, 0, 255));
                mat.copyTo(out, mask);

                // crop up to the border
                Log.d("ELLIPSE", ellipse.toString());
                mat = new Mat(out, ellipse.boundingRect());

                // also 'crop' the ellipse
                ellipse = new RotatedRect(new Point(ellipse.size.width / 2, ellipse.size.height / 2),
                        ellipse.size, ellipse.angle);
                ellipses.remove(0);
                ellipses.add(0, ellipse);

                data.put("mat", mat);
                data.put("bitmap", toBitmap(mat));
                break;
        }


        return Status.PASSED;
    }
}
