package gallerymine.model.support;

public enum ProcessStatus {
    PREPARING(false),
    STARTING(false),
    STARTED(false),
    RUNNING(false),
    TEARDOWN(false),
    FINISHED(true),
    FAILED(true);

    boolean finalStatus = false;

    ProcessStatus(boolean finalStatus) {
        this.finalStatus = finalStatus;
    }

    public boolean isFinalStatus() {
        return finalStatus;
    }
}
