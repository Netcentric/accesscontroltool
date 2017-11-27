package biz.netcentric.cq.tools.actool.history;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.slf4j.Logger;

public class ProgressTrackerListenerInstallationLogger extends PersistableInstallationLogger {

    private final ProgressTrackerListener listener;

    public ProgressTrackerListenerInstallationLogger(ProgressTrackerListener listener) {
        super();
        this.listener = listener;
    }

    @Override
    protected void addWarning(String warning) {
        listener.onMessage(ProgressTrackerListener.Mode.TEXT, warning, "");
        super.addWarning(warning);
    }

    @Override
    protected void addMessage(String message) {
        listener.onMessage(ProgressTrackerListener.Mode.TEXT, message, "");
        super.addMessage(message);
    }

    @Override
    public void addError(String error) {
        listener.onError(ProgressTrackerListener.Mode.TEXT, error, null);
        super.addError(error);
    }

    @Override
    public void addError(Logger log, String error, Throwable e) {
        if (e instanceof Exception) {
            listener.onError(ProgressTrackerListener.Mode.TEXT, error, (Exception)e);
            super.addError(error + " / e=" + e);
        } else {
            super.addError(log, error, e);
        }
    }

    @Override
    protected void addVerboseMessage(String message) {
        listener.onMessage(ProgressTrackerListener.Mode.TEXT, message, "");
        super.addVerboseMessage(message);
    }

}
