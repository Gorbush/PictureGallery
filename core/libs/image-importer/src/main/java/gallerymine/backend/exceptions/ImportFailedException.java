package gallerymine.backend.exceptions;

public class ImportFailedException extends Exception {

    public ImportFailedException() {
    }

    public ImportFailedException(String message) {
        super(message);
    }

    public ImportFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ImportFailedException(Throwable cause) {
        super(cause);
    }

    public ImportFailedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
