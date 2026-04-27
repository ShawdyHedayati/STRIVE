package com.strive.session.command;

import com.strive.model.DBRecord;
import com.strive.session.SessionState;

public class EditCommand<T extends DBRecord> implements Command {
    private final T previous;
    private final T updated;

    public EditCommand(T previous, T updated) {
        this.previous = previous;
        this.updated  = updated;
    }

    @Override public void apply(SessionState state) { state.update(updated); }
    @Override public void undo(SessionState state)  { state.update(previous); }

    public T getPrevious() { return previous; }
    public T getUpdated()  { return updated; }
}
