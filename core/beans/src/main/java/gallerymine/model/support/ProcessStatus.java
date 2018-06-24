package gallerymine.model.support;

public enum ProcessStatus {
    PREPARING(false),
    RESTARTING(false),

    STARTING(false),
    STARTED(false),

    RUNNING(false),

    TEARDOWN(false),

    FINISHED(true),
    ABANDONED(true),
    FAILED(true);

    boolean finalStatus = false;

    ProcessStatus(boolean finalStatus) {
        this.finalStatus = finalStatus;
    }

    public boolean isFinalStatus() {
        return finalStatus;
    }
}
