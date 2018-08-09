package com.hhu.yannick.coinclassificator.SQLite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.opencv.core.MatOfKeyPoint;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {

    SQLiteDatabase database;
    DatabaseHelper helper;

    public DatabaseManager(Context context){
        helper = new DatabaseHelper(context);
    }

    public void open(){
        try {
            database = helper.getWritableDatabase();
            Log.d("SQL", "p: " + database.getPath() + " v: " + database.getVersion());
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public void close(){
        try {
            helper.close();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    /*
    public void putTest(Mat test){
        ContentValues values = new ContentValues();
        values.put("Type", "TestMat");
        values.put("Coin_id", 0);
        values.put("Keypoints", new byte[]{(byte)100});
        Log.d("SQL", "put1");
        values.put("Descriptor", toDBBytes(test));
        values.put("Mask", new byte[]{(byte)100});
        database.insert("Feature", null, values);
    }

    public Mat getTest(){
        String sql = "SELECT Descriptor FROM Feature WHERE " +
                "Type = 'TestMat';";
        Cursor cursor = database.rawQuery(sql, null);
        cursor.moveToFirst();
        byte[] bytes = cursor.getBlob(0);
        cursor.close();

        database.delete("Feature", "Type = ?", new String[]{"TestMat"});

        return fromDBBytes(bytes);
    }*/

    public Map<String, CoinData[]> getCoins(){
        Map<String, CoinData[]> data = new HashMap<>();

        String sql = "SELECT Name, Value FROM Coin, Country WHERE Coin.Country_id = Country.id;";
        Cursor cursor = database.rawQuery(sql, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            String country = cursor.getString(0);
            int value = cursor.getInt(1);

            if(!data.containsKey(country)){
                data.put(country, new CoinData[3]);
            }
            data.get(country)[value] = new CoinData(value, country);
            cursor.moveToNext();
        }
        cursor.close();
        return data;
    }

    public void putFeatures(List<FeatureData> featureData, CoinData coin){
        // find the id corresponding to the coin
        String sql = "SELECT id FROM Coin, Country WHERE " +
                "Coin.Country_id = Country.id AND " +
                "County.Name = '" + coin.country + "' AND " +
                "Coin.Value = " + coin.value + ";";
        Cursor cursor = database.rawQuery(sql, null);
        cursor.moveToFirst();
        int coinId = cursor.getInt(0);
        cursor.close();

        // put the feature data into the database
        for (FeatureData feature: featureData) {
            // create the binary first
            ContentValues values = new ContentValues();
            values.put("Start", feature.start);
            values.put("Length", feature.length);
            database.insert("Binary",null, values);

            // directly find the id
            sql = "SELECT id FROM Binary WHERE " +
                    "Binary.Start = " + feature.start + " AND " +
                    "Binary.Length = " + feature.length + ";";
            cursor = database.rawQuery(sql, null);
            cursor.moveToFirst();
            int binId = cursor.getInt(0);
            cursor.close();

            // insert Feature
            values = new ContentValues();
            values.put("Type", feature.type);
            values.put("Coin_id", coinId);
            values.put("Binary_id", binId);
            database.insert("Feature", null, values);
        }
    }

    public void putFeature(FeatureData feature, CoinData coin) {
        // find the id corresponding to the coin
        String sql = "SELECT Coin.id FROM Coin, Country WHERE " +
                "Coin.Country_id = Country.id AND " +
                "Country.Name = '" + coin.country + "' AND " +
                "Coin.Value = " + coin.value + ";";
        Cursor cursor = database.rawQuery(sql, null);
        cursor.moveToFirst();
        int coinId = cursor.getInt(0);
        cursor.close();

        // create the binary first
        ContentValues values = new ContentValues();
        values.put("Start", feature.start);
        values.put("Length", feature.length);
        database.insert("Binary",null, values);

        // directly find the id
        sql = "SELECT id FROM Binary WHERE " +
                "Binary.Start = " + feature.start + " AND " +
                "Binary.Length = " + feature.length + ";";
        cursor = database.rawQuery(sql, null);
        cursor.moveToFirst();
        int binId = cursor.getInt(0);
        cursor.close();

        // insert Feature
        values = new ContentValues();
        values.put("Type", feature.type);
        values.put("Coin_id", coinId);
        values.put("Binary_id", binId);
        database.insert("Feature", null, values);
    }

    public Map<CoinData, FeatureData> getFeaturesByType(String type){
        Map<CoinData, FeatureData> data = new HashMap<>();

        String sql = "SELECT Country.Name, Coin.Value, Binary.Start, Binary.Length " +
                "FROM Feature, Coin, Country, Binary WHERE " +
                "Feature.Coin_id = Coin.id AND " +
                "Coin.Country_id = Country.id AND " +
                "Feature.Binary_id = Binary.id AND " +
                "Feature.Type = '" + type + "';";
        Cursor cursor = database.rawQuery(sql, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {

            FeatureData feature = new FeatureData(type);
            feature.start = cursor.getInt(2);
            feature.length = cursor.getInt(3);
            CoinData coinData = new CoinData(cursor.getInt(1), cursor.getString(0));

            data.put(coinData, feature);
            cursor.moveToNext();
        }
        cursor.close();

        return data;
    }

    public void putCoin(CoinData coinData){
        // find the id corresponding to the coin
        String sql = "SELECT Country.id FROM Country WHERE " +
                "Country.Name = '" + coinData.country + "';";
        Cursor cursor = database.rawQuery(sql, null);
        cursor.moveToFirst();
        int countryId = cursor.getInt(0);
        cursor.close();

        // put the coin data into the database
        ContentValues values = new ContentValues();
        values.put("Country_id", countryId);
        values.put("Value", coinData.value);
        database.insert("Coin", null, values);
    }

    public List<String> getCountrys(){
        List<String> data = new ArrayList<>();

        String sql = "SELECT Name FROM Country ;";
        Cursor cursor = database.rawQuery(sql, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            data.add(cursor.getString(0));
            cursor.moveToNext();
        }
        cursor.close();

        return data;
    }
    public void putCountry(String country, int binStart, int binLength){
        // create the binary first
        ContentValues values = new ContentValues();
        values.put("Start", binStart);
        values.put("Length", binLength);
        database.insert("Binary",null, values);

        // directly find the id
        String sql = "SELECT id FROM Binary WHERE " +
                "Binary.Start = " + binStart + " AND " +
                "Binary.Length = " + binLength + ";";
        Cursor cursor = database.rawQuery(sql, null);
        cursor.moveToFirst();
        int binId = cursor.getInt(0);
        cursor.close();

        Log.d("SQL", country + ", binId: " + binId);

        values = new ContentValues();
        values.put("Name", country);
        values.put("Binary_id", binId);
        database.insert("Country", null, values);
    }
}
