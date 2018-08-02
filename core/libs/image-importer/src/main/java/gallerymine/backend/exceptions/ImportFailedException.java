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

    public static Throwable findCause(Object ... params) {
        Throwable cause = null;
        if (params != null && params.length > 0) {
            if (params[params.length-1] instanceof Throwable) {
                cause = (Throwable) params[params.length-1];
            }
        }
        return cause;
    }

    public static String formatMessage(String message, Object... params) {
        if (params == null || params.length == 0) {
            return message;
        }
        return String.format(message, params);
    }

    public ImportFailedException(String message, Object... params) {
        super(formatMessage(message), findCause(params));
    }

    public ImportFailedException(Throwable cause) {
        super(cause);
    }

    public ImportFailedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
