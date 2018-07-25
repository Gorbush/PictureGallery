package gallerymine.model.support;

public enum InfoStatus {
    ANALYSING(false),
    APPROVING(false),
    SIMILAR(true),

    APPROVED(true),
    FAILED(true),
    SKIPPED(true),
    DUPLICATE(true);

    boolean finalStatus = false;

    InfoStatus(boolean finalStatus) {
        this.finalStatus = finalStatus;
    }

    public boolean isFinalStatus() {
        return finalStatus;
    }
}
