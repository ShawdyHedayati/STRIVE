package com.cobra.cache;

import com.cobra.types.Goal;
import com.cobra.types.Transaction;
import java.util.ArrayList;

// TODO: nest this inside of controller

public class CacheManager {
    // singleton - limit to only ONE CACHE
    private static CacheManager instance; // the class NOT obj

    private ArrayList<Transaction> transactionList;
    private ArrayList<Goal> goalList;

    private CacheManager() {}

    public static CacheManager getInstance() {
        if (instance == null) {
            instance = new CacheManager();
        }
        return instance;
    }

    public ArrayList<Transaction> getTransactionList() {
        return transactionList;
    }
    public ArrayList<Goal> getGoalList() {
        return goalList;
    }

    public void setTransactionList(ArrayList<Transaction> list) {
        this.transactionList = list;
    }
    public void setGoalList(ArrayList<Goal> list) {
        this.goalList = list;
    }
}