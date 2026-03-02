import java.util.ArrayList;

public class TransactionTable {
	private ArrayList<Transaction> transactions = new ArrayList<Transaction>();

	public void addTransaction(Transaction input) {
		transactions.add(input);
	}

	public void importTransactions(ArrayList<Transaction> inputs) {
		transactions = inputs;
	}

	// TODO: Add function for importing from SQLite DB
}
