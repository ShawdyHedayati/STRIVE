package com.strive.session;

import com.strive.model.DBRecord;

public class DeleteCommand<T extends DBRecord> implements Command {
    private final T record;

    public DeleteCommand(T record) { this.record = record; }

    @Override public void apply(SessionState state) { state.remove(record.getID()); }
    @Override public void undo(SessionState state)  { state.add(record); }

    public T getRecord() { return record; }
}
