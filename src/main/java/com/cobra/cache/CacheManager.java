package com.cobra.cache;

import com.cobra.types.Transaction;
import com.cobra.types.Limit;
import java.util.ArrayList;

public class CacheManager {
    // singleton - limit to only ONE CACHE
    private static CacheManager instance; // the class NOT obj

    private ArrayList<Transaction> transactionList = new ArrayList<>();
    private ArrayList<Limit> limitList = new ArrayList<>();

    private CacheManager() {}

    public static CacheManager getInstance() {
        if (instance == null) {
            instance = new CacheManager();
        }
        return instance;
    }

    public void setTransactionList(ArrayList<Transaction> list) {
        this.transactionList = new ArrayList<>(list);
    }

    public void setLimitList(ArrayList<Limit> list) {
        this.limitList = new ArrayList<>(list);
    }
}