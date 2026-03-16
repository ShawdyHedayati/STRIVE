package com.cobra;

import com.cobra.cache.CacheManager;
import com.cobra.types.Goal;
import com.cobra.types.Transaction;
import java.util.ArrayList;

public class Controller {
	private DBModel dbModel;
	private CacheManager cache;

	public Controller() {
		this.dbModel = new DBModel();
		this.dbModel.initDB(); // before fetch
		this.cache = CacheManager.getInstance();
		updateCache();

		System.out.println("Cache Loaded:");
		System.out.println("\tTransactions: " + cache.getTransactionList());
		System.out.println("\tGoals: " + cache.getGoalList());
	}

	public void updateCache() {
		cache.setTransactionList(dbModel.fetchTransactions());
		cache.setGoalList(dbModel.fetchGoals());
	}

	public ArrayList<Transaction> getTransactions() {
		return cache.getTransactionList();
	}

	public ArrayList<Goal> getGoals() {
		return cache.getGoalList();
	}
}