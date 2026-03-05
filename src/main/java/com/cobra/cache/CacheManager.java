package com.cobra.cache;

import com.cobra.models.Goal;
import com.cobra.models.Transaction;
import java.util.*;

/*
I lovvvvvvvvvve building midlayer stuff (idk what im doing?)
but this is for a mid layer cache that sides between the ui and database
UI does not talk to db directly
all read/write happens through here -> i am not sure if this is what ian wanted with how the dashboard works with the cache
waiting for db stuff really, so yay
*/

public class CacheManager {

    // singleton - limit to only ONE CACHE
    private static CacheManager instance; // the class NOT obj

    public static CacheManager getInstance(){
        if (instance == null) {
            instance = new CacheManager();
        }
        return instance;
    }

    private CacheManager(){
        // nothing can "new CacheManager"; getInstance()
        loadFromDatabase();
    }

    // internal maps
    // cannot be replace, just can change (context)
    // key -> ID (lookup, update and delete)
    private final Map<Integer, Transaction> transactionCache = new LinkedHashMap<>();
    private final Map <Integer, Goal> goalCache = new LinkedHashMap<>();

    // this will be replace with DB id when built
    private int nextTransactionID = 1;
    private int nextGoalID = 1;

    // placeholder data (for now, as we sit patiently for db to be cooked UP)
    // one time call
    private void loadFromDatabase(){
        // placeholder yay!! (temp List.of)
        List<Transaction> transactions = List.of(
                new Transaction(1, 24.99, "Food", 20260301),
                new Transaction(2, 9.99, "Fun", 20260301),
                new Transaction(3, 50.00, "Utilities", 20260228)
        );
        List<Goal> goals = List.of(
                new Goal(1, 150.00, "Living", 20260401),
                new Goal(2, 100.00, "Food", 20260401),
                new Goal(3, 50.00, "Fun", 20260401)
        );

        // push item into map using the ID
        for (Transaction t : transactions) {
            transactionCache.put(t.id(), t);
            if (t.id() >= nextTransactionID)
                nextTransactionID = t.id() + 1;
        }

        for (Goal g : goals) {
            goalCache.put(g.id(), g);
            if (g.id() >= nextGoalID)
                nextGoalID = g.id() + 1;
        }
    }

    // transactions - returns live, unmodified view of transactions in cache
    // getTransactions returns from cache
    // can be read but not untouchable (unmodifiable)
    public Collection<Transaction> getTransactions() {
        return Collections.unmodifiableCollection(transactionCache.values());
    }

    // create new transaction
    // increment id
    // put in map
    public Transaction addTransaction(String category, double amount, int date) {
        Transaction t = new Transaction(nextTransactionID++, amount, category, date);
        transactionCache.put(t.id(), t);

        return t;
    }

    // check for id to be able to overwrite
    // false if id not found -> somethin is up bud
    public boolean updateTransaction(int id, String category, double amount, int date) {
        if (!transactionCache.containsKey(id))
            return false;

        Transaction updated = new Transaction(id, amount, category, date);
        transactionCache.put(id, updated);

        return true;
    }

    // remove id
    // return false if there is nothing there to get rid of
    public boolean deleteTransaction(int id) {
        return transactionCache.remove(id) != null;
    }

    // goal -> reference methods of transaction
    public Collection<Goal> getGoals() {
        return Collections.unmodifiableCollection(goalCache.values());
    }

    public Goal addGoal(String category, double amount, int date) {
        Goal g = new Goal(nextGoalID++, amount, category, date);
        goalCache.put(g.id(), g);

        return g;
    }

    public boolean updateGoal(int id, String category, double amount, int date) {
        if (!goalCache.containsKey(id))
            return false;

        Goal updated = new Goal(id, amount, category, date);
        goalCache.put(id, updated);

        return true;
    }

    public boolean deleteGoal(int id)
    {
        return goalCache.remove(id) != null;
    }

    // stuff for charts ->>>>

    // group total spending by category
    public Map<String, Double> getSpendingByCategory() {
        Map<String, Double> result = new LinkedHashMap<>();

        for (Transaction t : transactionCache.values()) {
            result.merge(t.category(), t.amount(), Double::sum);
        }

        return result;
    }

    // take a single category and return total spent
    public double getTotalSpentForCategory(String category) {
        return transactionCache.values().stream().
                filter(t -> t.category().equalsIgnoreCase(category)). // naming does not need to be consistent
                mapToDouble(Transaction::amount).sum();
    }

}
