package strive;

import com.strive.bll.ChartCalculator;
import com.strive.bll.LimitCalculator;
import com.strive.bll.SpendingCalculator;
import com.strive.model.SpendingLimit;
import com.strive.model.Transaction;
import com.strive.util.CSVExporter;

import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class STRIVEAppTest {
        @Nested
        @DisplayName("Performance Tests")
        class PerformanceTests {

            @Test
            @DisplayName("pieChartData() with 10,000 transactions completes within 2 seconds")
            void pieChartData_largeDataset_completesWithinTimeLimit() {
                String[] categories = {
                        "Housing", "Utilities", "Transportation", "Food",
                        "Health", "Fun", "Personal", "Subscriptions", "Miscellaneous"
                };

                List<Transaction> transactions = new ArrayList<>();
                for (int i = 0; i < 10_000; i++) {
                    String category = categories[i % categories.length];
                    transactions.add(new Transaction(i, 10.0 + i, category, LocalDate.now()));
                }

                SpendingCalculator calc = new SpendingCalculator();

                long start   = System.currentTimeMillis();
                List<SpendingCalculator.PieSlice> result = calc.pieChartData(transactions);
                long elapsed = System.currentTimeMillis() - start;

                assertFalse(result.isEmpty(),
                        "Result should not be empty for a non-empty input");

                assertTrue(elapsed < 2000,
                        "pieChartData() took " + elapsed + "ms — expected under 2000ms");
            }

            @Test
            @DisplayName("CSVExporter.generate() with 5,000 transactions and 9 limits completes within 1 second")
            void csvExport_largeDataset_completesWithinTimeLimit() {
                String[] categories = {
                        "Housing", "Utilities", "Transportation", "Food",
                        "Health", "Fun", "Personal", "Subscriptions", "Miscellaneous"
                };

                List<Transaction> transactions = new ArrayList<>();
                for (int i = 0; i < 5_000; i++) {
                    String category = categories[i % categories.length];
                    transactions.add(new Transaction(i, 20.0 + i, category, LocalDate.now().minusDays(i % 30)));
                }

                List<SpendingLimit> limits = new ArrayList<>();
                for (int i = 0; i < categories.length; i++) {
                    limits.add(new SpendingLimit(i, 500.0, categories[i], LocalDate.now()));
                }

                CSVExporter exporter = new CSVExporter();

                long start = System.currentTimeMillis();
                String result = exporter.generate(transactions, limits);
                long elapsed = System.currentTimeMillis() - start;

                assertNotNull(result, "generate() must not return null");
                assertFalse(result.isBlank(), "generate() must not return a blank string");
                assertTrue(result.contains("STRIVE - Spending Report"),
                        "Output must contain the report header");

                assertTrue(elapsed < 1000,
                        "CSVExporter.generate() took " + elapsed + "ms — expected under 1000ms");
            }
        }

        @Nested
        @DisplayName("Accuracy Tests")
        class AccuracyTests {

            private final LimitCalculator limitCalc  = new LimitCalculator();
            private final ChartCalculator chartCalc  = new ChartCalculator();
            private final CSVExporter exporter   = new CSVExporter();

            @Test
            @DisplayName("fillPercent() returns exactly 100.0 when spending equals the limit")
            void fillPercent_atExactLimit_returns100() {
                SpendingLimit limit = new SpendingLimit(1, 100.0, "Food", LocalDate.now());
                List<Transaction> transactions = List.of(
                        new Transaction(1, 100.0, "Food", LocalDate.now())
                );

                double result = limitCalc.fillPercent(limit, transactions);

                assertEquals(100.0, result, 0.001,
                        "Spending exactly at the limit should produce a fill of 100.0");
            }


            @Test
            @DisplayName("isOverLimit() returns false when spending is just below the limit")
            void isOverLimit_justBelowLimit_returnsFalse() {
                SpendingLimit limit = new SpendingLimit(1, 100.0, "Food", LocalDate.now());
                List<Transaction> transactions = List.of(
                        new Transaction(1, 99.99, "Food", LocalDate.now())
                );

                assertFalse(limitCalc.isOverLimit(limit, transactions),
                        "Spending of $99.99 against a $100 limit must not trigger isOverLimit");
            }

            @Test
            @DisplayName("spentForCategory() ignores transactions from previous weeks")
            void spentForCategory_ignoresPreviousWeekTransactions() {
                List<Transaction> transactions = List.of(
                        new Transaction(1, 200.0, "Food", LocalDate.now().minusWeeks(2))
                );

                double result = limitCalc.spentForCategory("Food", transactions);

                assertEquals(0.0, result, 0.001,
                        "Transactions from two weeks ago must not count toward this week's spending");
            }

            @Test
            @DisplayName("avgBarGraphData() computes the correct per-category average")
            void avgBarGraphData_twoFoodTransactions_returnsCorrectAverage() {
                List<Transaction> transactions = List.of(
                        new Transaction(1, 20.0, "Food", LocalDate.now()),
                        new Transaction(2, 40.0, "Food", LocalDate.now())
                );

                double avg = chartCalc.avgBarGraphData(transactions).get("Food");

                assertEquals(30.0, avg, 0.001,
                        "Average of $20 and $40 should be $30.00");
            }

            @Test
            @DisplayName("allTransactionsSortedByDate() returns most recent transaction first")
            void allTransactionsSortedByDate_returnsMostRecentFirst() {
                List<Transaction> transactions = List.of(
                        new Transaction(1, 10.0, "Food", LocalDate.of(2025, 1,  1)),
                        new Transaction(2, 20.0, "Transportation", LocalDate.of(2025, 3, 15)),
                        new Transaction(3, 30.0, "Health", LocalDate.of(2025, 2, 10))
                );

                List<Transaction> sorted = chartCalc.allTransactionsSortedByDate(transactions);

                assertEquals(LocalDate.of(2025, 3, 15), sorted.get(0).date(),
                        "Most recent transaction (Mar 15) must appear first");
                assertEquals(LocalDate.of(2025, 1,  1), sorted.get(sorted.size() - 1).date(),
                        "Oldest transaction (Jan 1) must appear last");
            }

            @Test
            @DisplayName("generate() reports the correct total spending in the header")
            void generate_correctTotalSpending() {
                List<Transaction> transactions = List.of(
                        new Transaction(1, 10.0, "Food", LocalDate.now()),
                        new Transaction(2, 20.0, "Miscellaneous", LocalDate.now()),
                        new Transaction(3, 30.0, "Transportation", LocalDate.now())
                );

                String csv = exporter.generate(transactions, List.of());

                assertTrue(csv.contains("$60.00"),
                        "Total spending of $10 + $20 + $30 should appear as $60.00 in the report");
            }

            @Test
            @DisplayName("generate() shows 'No limit set' and '-' for categories without a limit")
            void generate_noLimitSet_showsCorrectPlaceholders() {
                List<Transaction> transactions = List.of(
                        new Transaction(1, 50.0, "Food", LocalDate.now())
                );

                String csv = exporter.generate(transactions, List.of());

                assertTrue(csv.contains("No limit set"),
                        "Category with no limit should display 'No limit set'");
                assertTrue(csv.contains(",-"),
                        "Remaining column should display '-' when no limit is set");
            }
        }

        @Nested
        @DisplayName("Repeatability Tests")
        class RepeatabilityTests {

            private final SpendingCalculator calc  = new SpendingCalculator();
            private final CSVExporter exporter = new CSVExporter();

            @Test
            @DisplayName("pieChartData() returns identical results across three consecutive calls")
            void pieChartData_repeatedCalls_returnIdenticalResults() {
                List<Transaction> transactions = List.of(
                        new Transaction(1, 50.0, "Food", LocalDate.now()),
                        new Transaction(2, 50.0, "Transportation", LocalDate.now())
                );

                List<SpendingCalculator.PieSlice> first  = calc.pieChartData(transactions);
                List<SpendingCalculator.PieSlice> second = calc.pieChartData(transactions);
                List<SpendingCalculator.PieSlice> third  = calc.pieChartData(transactions);

                assertEquals(first.size(), second.size(),
                        "Call 2 must return the same number of slices as call 1");
                assertEquals(first.size(), third.size(),
                        "Call 3 must return the same number of slices as call 1");

                for (int i = 0; i < first.size(); i++) {
                    assertEquals(first.get(i).category(), second.get(i).category(),
                            "Slice " + i + " category must match between calls 1 and 2");
                    assertEquals(first.get(i).percent(),  second.get(i).percent(),  0.001,
                            "Slice " + i + " percent must match between calls 1 and 2");
                    assertEquals(first.get(i).category(), third.get(i).category(),
                            "Slice " + i + " category must match between calls 1 and 3");
                    assertEquals(first.get(i).percent(),  third.get(i).percent(),   0.001,
                            "Slice " + i + " percent must match between calls 1 and 3");
                }
            }

            @Test
            @DisplayName("CSVExporter.generate() produces identical output across three consecutive calls")
            void csvGenerate_repeatedCalls_returnIdenticalResults() {
                List<Transaction> transactions = List.of(
                        new Transaction(1, 40.0, "Food", LocalDate.now()),
                        new Transaction(2, 60.0, "Housing", LocalDate.now())
                );
                List<SpendingLimit> limits = List.of(
                        new SpendingLimit(1, 200.0, "Food", LocalDate.now()),
                        new SpendingLimit(2, 500.0, "Housing", LocalDate.now())
                );

                String first  = exporter.generate(transactions, limits);
                String second = exporter.generate(transactions, limits);
                String third  = exporter.generate(transactions, limits);

                assertEquals(first, second,
                        "generate() must produce identical output on calls 1 and 2");
                assertEquals(first, third,
                        "generate() must produce identical output on calls 1 and 3");
            }
        }
    }