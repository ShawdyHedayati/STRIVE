# STRIVE

**Simple Tracker for Recording and Identifying Visual Expenses**

STRIVE is a personal finance desktop application built for students who want to understand and manage their spending habits. It lets you log transactions, set weekly category budgets, visualize breakdowns with charts, and export reports to CSV. All data is stored locally in a SQLite database with no accounts or cloud dependency required.

> COMMENTS ASSISTED BY CLAUDE.AI

---

## Features

- **Transaction tracking** -- add, edit, delete, and undo individual spending entries
- **Weekly spending limits** -- set a budget cap per category; limits reset automatically each Monday
- **Pie chart** -- see this week's spending broken down by category with hover tooltips
- **Trend line chart** -- compare this week's daily spending against your all-time daily average
- **Average spending bar chart** -- view average transaction amounts per category across all time
- **CSV export** -- generate a formatted spending report with category totals and limit comparisons
- **Persistent local storage** -- all data is saved to a SQLite file on your machine

---

## Architecture

STRIVE follows a layered MVC-style architecture. Each layer has a single responsibility and dependencies only flow downward.

```
UI Layer          DashboardView, ChartsView (JavaFX / FXML)
     |
Controller Layer  TransactionController, LimitController, NavigationController
     |
BLL Layer         SpendingCalculator, LimitCalculator, ChartCalculator, CSVExporter
     |
Session Layer     SessionManager, SessionState, Command pattern (Add/Edit/Delete)
     |
Model / DAO Layer TransactionDAO, LimitDAO (SQLite via JDBC)
```

---

## Project Structure

```
src/main/java/com/strive/
    STRIVEApp.java                  Application entry point and startup wiring
    config/
        AppContext.java             Static registry that bridges controllers to FXML views
    model/
        DBRecord.java               Interface requiring an integer primary key
        Transaction.java            Immutable record for a spending entry
        SpendingLimit.java          Immutable record for a weekly category budget
        dao/
            BaseDAO.java            Shared SQL execution helpers
            TransactionDAO.java     CRUD for the transactions table
            LimitDAO.java           CRUD for the goals table
    session/
        SessionManager.java         Central coordinator: state, command logs, flush
        SessionState.java           In-memory snapshot of all transactions and limits
        SessionListener.java        Observer interface for UI refresh notifications
        command/
            Command.java            Interface: apply() and undo()
            AddCommand.java         Adds a record to session state
            EditCommand.java        Replaces a record in session state
            DeleteCommand.java      Removes a record from session state
    controller/
        TransactionController.java  Transaction CRUD, undo, CSV export, chart data
        LimitController.java        Limit CRUD, weekly reset logic
        NavigationController.java   View switching and save/discard lifecycle
    bll/
        SpendingCalculator.java     Weekly spending totals and pie chart data
        LimitCalculator.java        Limit bar fill percentages and over-limit flags
        ChartCalculator.java        Trend line overlay and average bar graph data
    util/
        CategoryRegistry.java       Enum of all valid categories with display names and colours
        CSVExporter.java            Formats the spending report string (no file I/O)
        DateUtils.java              Week boundary helpers (ISO Monday-Sunday)
    ui/
        BaseView.java               Shared dialog helpers and save button sync
        DashboardView.java          Four-island main dashboard
        ChartsView.java             Three-island charts and history view

src/test/java/strive/
    STRIVEAppTest.java              BLL unit tests: performance, accuracy, repeatability

src/main/resources/
    views/dashboard.fxml            Dashboard layout
    views/charts.fxml               Charts layout
    views/styles.css                Shared stylesheet
    strive_test.db                  SQLite database file
```

---

## Building and Running

### Prerequisites

- Java 21 or later
- Maven 3.8 or later
- JavaFX 21 (pulled automatically by Maven via the pom)

### Run from source

```bash
git clone https://github.com/ShawdyHedayati/STRIVE.git
cd STRIVE
mvn compile
mvn javafx:run
```

### Run the tests

```bash
mvn test
```

---

## Usage

**Adding a transaction:** Select a category and date, enter an amount, and click Add. The transaction appears immediately in the Today's Entries table and the pie chart updates. Changes are not saved to disk until you click Save.

**Undoing a transaction:** Click the Undo button to reverse the most recent add, edit, or delete. Only transaction changes can be undone; limit changes are permanent.

**Setting a spending limit:** Click the Add Limit button on the dashboard, choose a category and weekly cap, and confirm. Only one limit per category is allowed at a time.

**Weekly reset:** Limits reset automatically on the first launch of each new week. All limits created before the current Monday are deleted and a Save is performed automatically so the reset persists.

**Exporting a CSV report:** Navigate to the Charts view and click Export CSV. A file chooser lets you choose where to save the report, which includes a summary header, a per-category breakdown with limit comparisons, and a full transaction history sorted by date.

**Saving:** Click the Save button in the footer of either view. The button changes from outlined to filled (blue) whenever there are unsaved changes. Closing the window with unsaved changes shows a confirmation dialog before exiting.

---

## Contributors

| Name | Role                                     |
|---|------------------------------------------|
| Shawdy Hedayati | Project Manager, Developer, Documentaion |
| Alex | User Interface, Documentation            |
| Ian | Model development                        |
| Robert | User Interface, Testing, Documentation   |