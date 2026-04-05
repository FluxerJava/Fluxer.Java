package app.fluxer.exception;

public class FluxerApiException extends RuntimeException {
    private final int statusCode;
    private final String responseBody;

    public FluxerApiException(int statusCode, String responseBody) {
        super("Fluxer API request failed with status " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
