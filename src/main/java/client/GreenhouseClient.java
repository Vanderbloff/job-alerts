package client;

import model.Job;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GreenhouseClient implements JobBoardClient {

    private static final String BASE_URL = "https://boards-api.greenhouse.io/v1/boards/%s/jobs?content=false";
    private final ObjectMapper objectMapper;

    public GreenhouseClient() {
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
            String companyName = jobNode.get("company_name").asString();
            String location = jobNode.path("location").path("name").asString(null);
            String firstPublished = jobNode.get("first_published").asString(null);
            String url = jobNode.get("absolute_url").asString();

            jobs.add(new Job(id, title, companyName, location, firstPublished, url));
        }

        return jobs;
    }
}