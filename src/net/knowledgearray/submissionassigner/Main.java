package net.knowledgearray.submissionassigner;

import com.google.gson.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class Main {
    
    // Script config
    private static final String BASE_URL = "https://review-api.udacity.com/api/v1";
    private static final String GET_CERTS_URL = "{}/me/certifications.json".replace("{}",BASE_URL);
    private static final String GET_ME_REQUEST_URL = "{}/me/submission_requests.json".replace("{}",BASE_URL);
    private static final String GET_ME_WAITS_URL = "{}/submission_requests/{sid}/waits.json".replace("{}",BASE_URL);
    private static final String POST_CREATE_REQUEST_URL = "{}/submission_requests.json".replace("{}",BASE_URL);
    private static final String DELETE_URL_TMPL = "{}/submission_requests/{sid}.json".replace("{}", BASE_URL);
    private static final String GET_ME_REQUEST_WITH_ID_URL = "{}/submission_requests/{sid}.json".replace("{}", BASE_URL);
    private static final String REFRESH_URL_TMPL = "{}/submission_requests/{sid}/refresh.json".replace("{}", BASE_URL);
    private static final String REVIEW_URL = "https://review.udacity.com/#!/submissions/{sid}";
    private static final String ASSIGNED_COUNT_URL = "{}/me/submissions/assigned_count.json".replace("{}",BASE_URL);

    private static final int REFRESH_INTERVAL = (1000 * 60 * 50); // 50 minutes

    private static String apiToken;
    private static String projectRequestString;
    private static HashMap<Integer, String> projectMap = new HashMap<>();

    private static int submissionId;
    private static int counter = 0;

    private Main() {
        throw new AssertionError("No Main instances for you!");
    }

    private static void displayUsage() {
        System.out.println("Usage: --auth-token [token] -d [optional: enable debug]");
    }

    private static HashMap<String, String> createQueryParamMap(String[] keys, String[] values){

        if(keys.length != values.length){
            throw new IllegalArgumentException("Array sizes must be the same.");
        }

        if(keys.length == 0){
            return null;
        }

        HashMap<String, String> queryMap = new HashMap<>();

        for(int i = 0; i < keys.length; i++){
            queryMap.put(keys[i], values[i]);
        }

        return queryMap;
    }

    private static Response request(String url, HashMap<String, String> urlPlacements, HashMap<String, String> params, String method, byte[] streamBytes) {
        String response;
        StringBuffer buffer;
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        try {
            // Replace place holders
            if(urlPlacements != null) {
                for (Map.Entry<String, String> entry : urlPlacements.entrySet()) {
                    url = url.replace(entry.getKey(), entry.getValue());
                }
            }

            URL requestUrl = new URL(url);
            urlConnection = (HttpURLConnection) requestUrl.openConnection();
            urlConnection.setReadTimeout(5500);
            urlConnection.setConnectTimeout(5500);
            urlConnection.setRequestMethod(method);

            if(params != null) {
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    urlConnection.addRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            if(streamBytes != null){

                urlConnection.setDoOutput(true);
                OutputStream out = urlConnection.getOutputStream();
                out.write(streamBytes);
            }

            urlConnection.connect();

            InputStream inputStream = urlConnection.getResponseCode() >= 400 ? urlConnection.getErrorStream() :  urlConnection.getInputStream();
            buffer = new StringBuffer();
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }

            if (buffer.length() == 0) {
                return new Response("", urlConnection.getResponseCode());
            }
            response = buffer.toString();
            return new Response(response, urlConnection.getResponseCode());

        } catch (IOException e) {
            System.err.print("Error: " + e.getMessage());
            return new Response("", -1);
        }
        finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    System.err.println("Error closing stream: " + e.getMessage());
                }
            }
        }
    }

    private static Response sendSubmissionRefresh() {
        return request(
                REFRESH_URL_TMPL.replace("{sid}", String.valueOf(submissionId)),
                null,
                createQueryParamMap(new String[]{"Authorization","Content-Length", "Content-Type"}, new String[]{apiToken,"0", "application/json"}),
                "GET",
                null);
    }

    private static void waitForAssignedCount() {
        for(;;) {
            Response countResponse = sendAssignedCountRequest();
            int assignedCount = new JsonParser().parse(countResponse.getResponseString()).getAsJsonObject().get("assigned_count").getAsInt();

            if(assignedCount == 2) {
                try {
                    System.out.println("Waiting until assigned count < 2");
                    Thread.sleep(1000 * 20); // wait 20 seconds
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            else{
                break;
            }
        }
    }

    private static boolean checkIfFulfilled(Response submissionResponse) {
        JsonObject obj = new JsonParser().parse(submissionResponse.getResponseString()).getAsJsonObject();

        boolean isFulfilled = obj.get("status").getAsString().equals("fulfilled");
        if(isFulfilled){
            displayAssignedMessage(obj);
        }
        return isFulfilled;
    }

    private static void displayAssignedMessage(JsonObject obj) {
        System.out.println();
        System.out.println("You have been assigned a new project! See it here: " + REVIEW_URL.replace("{sid}", String.valueOf(obj.get("submission_id").getAsInt())));
    }

    private static void checkIfFulfilledPrior(Response submissionResponse) {
        JsonObject respItem = new JsonParser().parse(submissionResponse.getResponseString()).getAsJsonObject();
        if(respItem.get("status").getAsString().equals("fulfilled")){
            displayAssignedMessage(respItem);
        }
    }

    private static Response sendSubmissionRequest() {
        return request(
                POST_CREATE_REQUEST_URL,
                null,
                createQueryParamMap(new String[]{"Authorization","Content-Length", "Content-Type"}, new String[]{apiToken,"0", "application/json"}),
                "POST",
                projectRequestString.getBytes());
    }

    private static Response sendWaitsRequest() {
        return request(
                GET_ME_WAITS_URL.replace("{sid}", String.valueOf(submissionId)),
                null,
                createQueryParamMap(new String[]{"Authorization","Content-Length", "Content-Type"}, new String[]{apiToken,"0", "application/json"}),
                "GET",
                null);
    }

    private static Response sendAssignedCountRequest() {
        return request(
                ASSIGNED_COUNT_URL,
                null,
                createQueryParamMap(new String[]{"Authorization","Content-Length", "Content-Type"}, new String[]{apiToken,"0", "application/json"}),
                "GET",
                null);
    }

    private static Response getSubmissionRequest() {
        return request(
                GET_ME_REQUEST_URL,
                null,
                createQueryParamMap(new String[]{"Authorization", "Content-Type"}, new String[]{apiToken, "application/json"}),
                "GET",
                null);

    }

    private static Response getSubmissionRequestWithId() {
        return request(
                GET_ME_REQUEST_WITH_ID_URL.replace("{sid}", String.valueOf(submissionId)),
                null,
                createQueryParamMap(new String[]{"Authorization", "Content-Type"}, new String[]{apiToken, "application/json"}),
                "GET",
                null);

    }


    private static Response deleteSubmissionRequest() {
        return request(
                DELETE_URL_TMPL.replace("{sid}", String.valueOf(submissionId)),
                null,
                createQueryParamMap(new String[]{"Authorization", "Content-Type"}, new String[]{apiToken, "application/json"}),
                "DELETE",
                null);

    }

    private static String getProjectName(JsonObject object) {
        return object.get("project").getAsJsonObject().get("name").getAsString();
    }

    private static int getProjectid(JsonObject object) {
        return object.get("project").getAsJsonObject().get("id").getAsInt();
    }

    public static void main(String[] argv) {

        if (argv.length < 2) {
            displayUsage();
            System.exit(1);
        }
        if (!argv[0].equalsIgnoreCase("--auth-token")) {
            displayUsage();
            System.exit(1);
        }

        apiToken = argv[1];

        final Scanner scanner = new Scanner(System.in);

        // Get Certifications
        System.out.println("Requesting Certifications...");
        String certResponse = request(
                GET_CERTS_URL,
                null,
                createQueryParamMap(new String[]{"Authorization", "Content-Length"}, new String[]{apiToken, "0"}),
                "GET",
                null)
                .getResponseString();

        JsonArray certJsonArray = new JsonParser().parse(certResponse).getAsJsonArray();

        final List<Project> projectRequestList = new ArrayList<>();

        Iterator<JsonElement> certIterator = certJsonArray.iterator();
        certIterator.forEachRemaining((element)->{

            JsonObject certObject = element.getAsJsonObject();

            if(certObject.get("status").toString().equals("\"certified\"")){

                String projectName = getProjectName(certObject);
                int projectId = getProjectid(certObject);

                System.out.println("Add " + getProjectName(certObject) + " to submission request? (y/n) ");
                String input = scanner.nextLine().toLowerCase().trim();

                if(input.equals("y")){
                    projectMap.put(projectId, projectName);
                    projectRequestList.add(new Project(Integer.valueOf(certObject.get("project_id").toString()), "en-us"));
                }
            }
        });

        if(projectRequestList.isEmpty()){
            System.out.println("No projects have been selected! Exiting...");
            System.exit(0);
        }

        System.out.print("Set delay in minutes: ");
        try {
            Thread.sleep(Long.valueOf(scanner.nextLine()) *1000*60);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }

        System.out.println("Submitting requests for " + projectRequestList.size() + " projects in form of: ");
        System.out.println("\"projects\"" + ":\n\t" + new Gson().toJson(projectRequestList));
        System.out.println("Polling for new submissions.");

        projectRequestString = "{\"projects\":" + new Gson().toJson(projectRequestList)+"}";

        // Check if submission request exists.
        Response submissionRequest = getSubmissionRequest();
        if(!submissionRequest.getResponseString().equals("[]")){
            System.out.println("Existing submission request detected: " + submissionRequest.getResponseString());
            System.out.print("Deleting....");
            submissionId = new JsonParser().parse(submissionRequest.getResponseString()).getAsJsonArray().get(0).getAsJsonObject().get("id").getAsInt();
            deleteSubmissionRequest();
            System.out.println("Deleted.");
        }

        waitForAssignedCount();

        Response newSubmissionResp = sendSubmissionRequest();
        System.out.println("Request Submitted: " + newSubmissionResp.getResponseString() );
        submissionId = new JsonParser().parse(newSubmissionResp.getResponseString()).getAsJsonObject().get("id").getAsInt();
        checkIfFulfilledPrior(newSubmissionResp);

        final long[] refreshMilis = {System.currentTimeMillis() + REFRESH_INTERVAL};

        // Kick off pulling
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2);
        executor.scheduleAtFixedRate(()->{

            System.out.println("Checking Request #" + (++counter));

            waitForAssignedCount();

            Response submissionResponse = getSubmissionRequestWithId();
            if (checkIfFulfilled(submissionResponse)) {
                Response newSubmissionResponse = sendSubmissionRequest();
                submissionId = new JsonParser().parse(newSubmissionResponse.getResponseString()).getAsJsonObject().get("id").getAsInt();
                refreshMilis[0] = System.currentTimeMillis() + REFRESH_INTERVAL; // reset timer
            }
            else{
                System.out.println("No projects. Here is your position in the queue:");
                Response waitsResponse = sendWaitsRequest();
                JsonArray waitsArray = new JsonParser().parse(waitsResponse.getResponseString()).getAsJsonArray();

                for(int i = 0; i < waitsArray.size(); i++){
                    int projectId = waitsArray.get(i).getAsJsonObject().get("project_id").getAsInt();
                    int position = waitsArray.get(i).getAsJsonObject().get("position").getAsInt();
                    System.out.println(String.format("%-75s: %s" , (projectMap.get(projectId)), ("Position = " + position) ));
                }
            }

            // Submission request is expired.
            if (System.currentTimeMillis() >= refreshMilis[0]) {
                System.out.print("Refreshing request...");
                Response refreshResponse = sendSubmissionRefresh();

                if(refreshResponse.getResponseCode() == 404){
                    System.out.println("Unable to refresh/no active submission request found. Creating new one.");
                    deleteSubmissionRequest(); // Delete if exists
                    Response newResponse = sendSubmissionRequest();
                    submissionId = new JsonParser().parse(newResponse.getResponseString()).getAsJsonArray().get(0).getAsJsonObject().get("id").getAsInt();
                }
                else {

                    JsonArray array = new JsonParser().parse(refreshResponse.getResponseString()).getAsJsonArray();

                    submissionId = array.get(0).getAsJsonObject().get("id").getAsInt();

                    System.out.println("Refresh Successful.");
                }
                refreshMilis[0] = System.currentTimeMillis() + REFRESH_INTERVAL;
            }
            System.out.println("-=-=-=-=-=-=-=-=-=-=-=-=-=-");
        },0, 3, TimeUnit.MINUTES);
    }
}
