package com.hhu.yannick.coinclassificator.AsyncProcessor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;

import com.hhu.yannick.coinclassificator.SQLite.DatabaseManager;

public class DatabaseLoader extends AsyncTask<Integer, Integer, Integer>{

    private OnTaskCompleted listener;
    private DatabaseManager databaseManager;
    private Context context;
    private String file;
    public Bitmap bitmap;

    public DatabaseLoader(OnTaskCompleted listener, DatabaseManager databaseManager, String file, Context context){
        this.listener = listener;
        this.databaseManager = databaseManager;
        this.file = file;
        this.context = context;
    }

    @Override
    protected Integer doInBackground(Integer... integers) {
        try {
            // open the database connection
            databaseManager.open();

            // load stored temp-bitmap
            bitmap = BitmapFactory.decodeStream(context.openFileInput(file));
            bitmap = bitmap.copy( Bitmap.Config.ARGB_8888 , true);
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return 1;
    }

    @Override
    protected void onPostExecute(Integer integer) {
        super.onPostExecute(integer);

        // notify listener that we finished loading the database
        listener.onTaskCompleted();
    }
}
