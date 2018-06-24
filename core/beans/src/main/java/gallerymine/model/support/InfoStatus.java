package gallerymine.model.support;

public enum InfoStatus {
    ANALYSING(false),
    APPROVING(false),
    APPROVED(true),
    FAILED(true),
    SIMILAR(true),
    DUPLICATE(true);

    boolean finalStatus = false;

    InfoStatus(boolean finalStatus) {
        this.finalStatus = finalStatus;
    }

    public boolean isFinalStatus() {
        return finalStatus;
    }
}
