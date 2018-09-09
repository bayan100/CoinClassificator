package com.hhu.yannick.coinclassificator.SQLite;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;

import java.io.File;

public class DatabaseHelper extends SQLiteAssetHelper {


    public DatabaseHelper(Context context){
        super(context, "CoinDatabase.db", null, 1);

        Log.d("SQL", "path: " + context.getDatabasePath("CoinDatabase.db").getAbsolutePath());

        String path = context.getDatabasePath("Coins.db").getAbsolutePath();
        path = path.substring(0, path.lastIndexOf('/'));
        Log.d("SQL", "path2: "+ path);
        File directory = new File(path);
        Log.d("SQL", "path3: "+ path);
        File[] files = directory.listFiles();
        Log.d("SQL", "Size: "+ files.length);
        for (int i = 0; i < files.length; i++)
        {
            Log.d("SQL", "FileName: " + files[i].getName() + " size: " + files[i].length());
        }
    }

    /*@Override
    public void onCreate(SQLiteDatabase db) {
        Log.d("SQL", "Couldn't find database!");
    }*/

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d("SQL", "Upgraded database?");
    }
}
