package net.knowledgearray.submissionassigner;

/**
 * Created by Anthony M. Santiago on 8/8/2016.
 */
public final class Response {

    /**
     * The response string
     */
    private String mResponse;

    /**
     * The response code
     */
    private int mResponseCode;

    /**
     * Constructors a {@link Response} object with a response string and associated response code.
     * @param response
     * @param responseCode
     */
    public Response(String response, int responseCode){
        mResponse = response;
        mResponseCode = responseCode;
    }

    public String getResponseString() {
        return mResponse;
    }

    public int getResponseCode() {
        return mResponseCode;
    }
}
