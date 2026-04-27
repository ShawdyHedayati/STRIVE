package com.strive.session.command;

import com.strive.model.DBRecord;
import com.strive.session.SessionState;

public class AddCommand<T extends DBRecord> implements Command {
    private final T record;

    public AddCommand(T record) { this.record = record; }

    @Override public void apply(SessionState state) { state.add(record); }
    @Override public void undo(SessionState state)  { state.remove(record.id()); }

    public T getRecord() { return record; }
}
