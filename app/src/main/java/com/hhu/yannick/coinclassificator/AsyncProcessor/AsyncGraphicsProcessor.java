package com.hhu.yannick.coinclassificator.AsyncProcessor;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.GraphicsProcessor;

import java.util.ArrayList;
import java.util.Map;

public class AsyncGraphicsProcessor extends AsyncTask<Integer, Integer, Integer>
{
    protected ArrayList<GraphicsProcessor> task;
    private OnTaskCompleted listener;
    public Map<String, Object> result;

    public AsyncGraphicsProcessor(OnTaskCompleted listener) {
        this.task = new ArrayList<GraphicsProcessor>();
        this.listener = listener;
    }

    @Override
    protected Integer doInBackground(Integer... ints)
    {
        Log.d("AGP", "len: " + task.size());
        // run the computational tasks
        for(int i = 0; i < task.size(); i++) {
            GraphicsProcessor.Status status = task.get(i).execute();

            // update progress
            publishProgress((int)((i / (float)task.size()) * 100f));

            // task passed, give data to next processor
            if (status == GraphicsProcessor.Status.PASSED) {
                if (i + 1 < task.size()) {
                    task.get(i + 1).passData(task.get(i).getData());
                }

                // successfully completed all tasks
                else {
                    // set the result
                    result = task.get(task.size() - 1).getData();

                    return 1;
                }
            }
            else if (status == GraphicsProcessor.Status.FAILED) {
                Log.e("AsycGP", "Process Nr. " + i + " (" + task.get(i).task + ") failed to execute");
                return -i;
            }
        }
        return 0;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        // debug
        Log.d("GraphicsProcessor","(" + this.getClass().getName() + ") Progress: " + (Math.round((values[0] / 100f) * task.size() + 1)) + "/" + task.size());
    }

    @Override
    protected void onPostExecute(Integer integer) {
        // notify that tasks are completed and remove reference to caller
        if(listener != null){
            listener.onTaskCompleted();
            listener = null;
        }
    }
}
