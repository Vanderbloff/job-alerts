package client;

import model.Job;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LeverClient implements JobBoardClient {

    private static final String BASE_URL = "https://api.lever.co/v0/postings/%s?mode=json";
    private final ObjectMapper objectMapper;

    public LeverClient() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL;
    }

    @Override
    public List<Job> parseJobs(String responseBody, String displayName) {
        JsonNode root = objectMapper.readTree(responseBody);

        if (!root.isArray()) {
            return Collections.emptyList();
        }

        List<Job> jobs = new ArrayList<>();

        for (JsonNode jobNode : root) {
            String id = jobNode.get("id").asString();
            String title = jobNode.get("text").asString();
            String location = jobNode.path("categories").path("location").asString(null);
            String createdAt = parseTimestamp(jobNode.get("createdAt"));
            String url = jobNode.get("hostedUrl").asString();

            jobs.add(new Job(id, title, displayName, location, createdAt, url));
        }

        return jobs;
    }

    private String parseTimestamp(JsonNode createdAtNode) {
        if (createdAtNode == null || createdAtNode.isNull()) return null;
        long epochMillis = createdAtNode.longValue();
        return Instant.ofEpochMilli(epochMillis).toString();
    }
}