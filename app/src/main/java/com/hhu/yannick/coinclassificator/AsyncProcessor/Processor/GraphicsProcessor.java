package com.hhu.yannick.coinclassificator.AsyncProcessor.Processor;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.util.HashMap;
import java.util.Map;

public class GraphicsProcessor {

    public String task = "Undefined";
    protected Map<String, Object> data = new HashMap<String, Object>();
    protected Map<String, Object> parameter = new HashMap<String, Object>();

    public enum Status {
        UNDEFINED,
        PASSED,
        FAILED
    }

    public final Status execute(){
        // start timer
        long starttime = System.nanoTime();

        // execute the main Process (overridden by child class)
        Status status = executeProcess();

        // timer stop
        Log.d("TIMER", "Task " + task + " completed in: " + ((System.nanoTime() - starttime) / 1000000) + " ms");
        return status;
    }

    protected Status executeProcess(){
        return Status.PASSED;
    }

    public void passData(Map<String, Object> data) {
        if(this.data == null)
            this.data = data;
        else {
            for(String s : data.keySet()) {
                if(!this.data.containsKey(s))
                    this.data.put(s, data.get(s));
            }
        }
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void set(String key, Object value){
        data.put(key, value);
    }

    public void setImage(Bitmap bitmap) {
        data.put("bitmap", bitmap);
        data.put("mat", toMat(bitmap));
    }

    public void setParameter(String key, Object value){
        parameter.put(key, value);
    }

    public Object getParameter(String key){
        return parameter.get(key);
    }

    protected float getFloat(String key){ return (Float)parameter.get(key); }
    protected int getInt(String key){ return (int)parameter.get(key); }
    protected String getString(String key){ return (String) parameter.get(key); }

    public Mat toMat(Bitmap bitmap){
        Mat image = new Mat();
        Bitmap bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Utils.bitmapToMat(bmp32, image);
        return image;
    }

    public Bitmap toBitmap(Mat image)
    {
        Bitmap bmp32 = Bitmap.createBitmap(image.width(), image.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(image, bmp32);
        return bmp32;
    }
}
