package teammates.ui.webapi;

/**
 * Exception thrown when a controller class is failed to be instantiated from a given resource URL.
 */
public class ActionMappingException extends Exception {

    private final int statusCode;

    public ActionMappingException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

}
