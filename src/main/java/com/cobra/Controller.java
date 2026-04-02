package com.cobra;

import com.cobra.cache.CacheManager;
//import com.cobra.types.Goal;
//import com.cobra.types.Transaction;
//import com.cobra.types.*;
import com.cobra.types.Statement;
import com.cobra.types.Transaction;
import java.util.ArrayDeque;
import java.util.ArrayList;

public class Controller {
	private DBModel dbModel;
	private CacheManager cache;

	private final ArrayDeque<Statement> actionQueue = new ArrayDeque<>();

	private int nextId= 1;

	public Controller() {
		this.dbModel = DBModel.getInstance();
		this.dbModel.initDB(); // before fetch
		this.cache = CacheManager.getInstance();
		updateCache();

//		System.out.println("Cache Loaded:");
//		System.out.println("\tTransactions: " + cache.getTransactionList());
//		System.out.println("\tGoals: " + cache.getGoalList());
	}

	// event listener

	public void onInsertRequested(double amount, String category) {
//		Statement stmt = Statement.insertTransaction(nextId++, amount, category);
//
//		actionQueue.add(stmt);
//		System.out.println("[Controller] Queued: " + stmt);
//
//		flushQueue();
		System.out.println("[Controller] INSERT requested: " + amount + " / " + category);
	}

	// queue ->->-> model

	private void flushQueue() {
		while (!actionQueue.isEmpty()) {
			Statement stmt = actionQueue.poll();
			//dbModel.ingestStatement(stmt);
			System.out.println("[Controller] Would flush: " + stmt);
		}
		updateCache();
	}

	public void updateCache() {
		cache.setTransactionList(dbModel.fetchTransactions());
//		cache.setGoalList(dbModel.fetchGoals());

		// TODO: updateView()
		// notify the view layer once view is implemented
	}

	public ArrayList<Transaction> getTransactions() {
		return cache.getTransactionList();
	}

//	public ArrayList<Goal> getGoals() {
//		return cache.getGoalList();
//	}

	// TODO: getData()
	// pull data from model layer
}