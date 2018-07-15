package gallerymine.backend.exceptions;

public class ImageApproveException extends Exception {

    public ImageApproveException() {
    }

    public ImageApproveException(String message) {
        super(message);
    }

    public ImageApproveException(String message, Throwable cause) {
        super(message, cause);
    }

    public ImageApproveException(Throwable cause) {
        super(cause);
    }

    public ImageApproveException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
