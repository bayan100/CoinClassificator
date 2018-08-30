package com.hhu.yannick.coinclassificator.AsyncProcessor;

import android.graphics.Bitmap;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.BlurGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.ContourGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.EllipseGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.GraphicsProcessor;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.ResizeGP;

import java.util.ArrayList;

public class DrawEllipseAGP extends AsyncGraphicsProcessor {

    public DrawEllipseAGP(Bitmap bitmap, OnTaskCompleted listener)
    {
        super(listener);

        ArrayList<GraphicsProcessor> processors = new ArrayList<>();
        ResizeGP gp = new ResizeGP(ResizeGP.Type.LINEAR);
        gp.set("bitmap", bitmap.copy(bitmap.getConfig(), true));
        processors.add(gp);
        processors.add(new BlurGP(BlurGP.Type.MEDIAN));
        processors.add(new ContourGP(ContourGP.Type.CANNYEDGE));
        processors.add(new ContourGP(ContourGP.Type.FIND));
        processors.add(new ContourGP(ContourGP.Type.SPLIT));
        processors.add(new ContourGP(ContourGP.Type.FILTER));
        processors.add(new ContourGP(ContourGP.Type.MERGE));
        processors.add(new EllipseGP(EllipseGP.Type.FIND));
        // and draw ellipse
        gp = new ResizeGP(ResizeGP.Type.LINEAR);
        gp.set("bitmap", bitmap.copy(bitmap.getConfig(), true));
        gp.set("mat", gp.toMat((Bitmap)gp.getData().get("bitmap")));
        processors.add(gp);
        processors.add(new EllipseGP(EllipseGP.Type.DRAW));

        task = processors;
    }
}
