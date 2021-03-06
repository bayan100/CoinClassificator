package com.hhu.yannick.coinclassificator.AsyncProcessor;

import android.graphics.Bitmap;

import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.BlurGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.ContourGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.EllipseGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.GraphicsProcessor;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.ResizeGP;

import java.util.ArrayList;

public class ContourAGP extends AsyncGraphicsProcessor {
    public ContourAGP(Bitmap bitmap, OnTaskCompleted listener)
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

        // and draw ellipse
        //gp = new ResizeGP(ResizeGP.Type.LINEAR);
        //gp.setImage(bitmap.copy(bitmap.getConfig(), true));
        //processors.add(gp);
        processors.add(new ContourGP(ContourGP.Type.DRAW));

        task = processors;
    }
}
