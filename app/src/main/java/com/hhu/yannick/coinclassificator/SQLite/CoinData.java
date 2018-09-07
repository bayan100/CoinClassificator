package com.hhu.yannick.coinclassificator.SQLite;

public class CoinData {
    // Value 0 = 1/2 Euro, 1 = Golden Coins, 2 = Copper Coins
    // Value 3 = 2 Euro, 4 = 1 Euro, 5 = 50 Cent, 6 = 20 Cent, 7 = 10 Cent, 8 = 5 Cent, 9 = 2 Cent, 10 = 1 Cent
    public int value;
    public int version;
    public String country;

    public CoinData(int value, int version, String country){
        this.value = value;
        this.version = version;
        this.country = country;
    }

    public static String valueToString(int value){
        switch (value){
            case -1: return "";
            case 0: return "1/2 Euro";
            case 1: return "50/20/10 Cent";
            case 2: return "5/2/1 Cent";
            case 3: return "2 Euro";
            case 4: return "1 Euro";
            case 5: return "50 Cent";
            case 6: return "20 Cent";
            case 7: return "10 Cent";
            case 8: return "5 Cent";
            case 9: return "2 Cent";
            case 10: return "1 Cent";
        }
        return "Undefined";
    }
}
