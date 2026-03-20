package client;

import model.Job;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AshbyClient implements JobBoardClient {

    private static final String BASE_URL = "https://api.ashbyhq.com/posting-api/job-board/%s";

    private final ObjectMapper objectMapper;

    public AshbyClient() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL;
    }

    @Override
    public List<Job> parseJobs(String responseBody, String displayName) {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode jobsArray = root.get("jobs");

        if (jobsArray == null || !jobsArray.isArray()) {
            return Collections.emptyList();
        }

        List<Job> jobs = new ArrayList<>();

        for (JsonNode jobNode : jobsArray) {
            String id = jobNode.get("id").asString();
            String title = jobNode.get("title").asString();
            String location = jobNode.path("location").asString(null);
            String publishedAt = jobNode.path("publishedAt").asString(null);
            String url = jobNode.get("jobUrl").asString();

            jobs.add(new Job(id, title, displayName, location, publishedAt, url));
        }

        return jobs;
    }
}