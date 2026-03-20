package client;

import model.Job;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorkableClient implements JobBoardClient {

    private static final String BASE_URL = "https://apply.workable.com/api/v1/widget/accounts/%s";

    private final ObjectMapper objectMapper;

    public WorkableClient() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL;
    }

    @Override
    public List<Job> parseJobs(String responseBody, String companyName) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        String resolvedCompanyName = root.path("name").asString(companyName);
        JsonNode jobsArray = root.get("jobs");

        if (jobsArray == null || !jobsArray.isArray()) {
            return Collections.emptyList();
        }

        List<Job> jobs = new ArrayList<>();

        for (JsonNode jobNode : jobsArray) {
            String id = jobNode.get("shortcode").asString();
            String title = jobNode.get("title").asString();
            String location = resolveLocation(jobNode);
            String publishedOn = resolvePublishedOn(jobNode);
            String url = jobNode.get("url").asString();

            jobs.add(new Job(id, title, resolvedCompanyName, location, publishedOn, url));
        }

        return jobs;
    }

    private String resolveLocation(JsonNode jobNode) {
        String city = jobNode.path("city").asString(null);
        String state = jobNode.path("state").asString(null);

        // treat empty strings as null
        if (city != null && city.isBlank()) city = null;
        if (state != null && state.isBlank()) state = null;

        if (city != null && state != null) return city + ", " + state;
        if (city != null) return city;
        return state;
    }

    private String resolvePublishedOn(JsonNode jobNode) {
        String raw = jobNode.path("published_on").asString(null);
        if (raw == null || raw.isBlank()) return null;
        return raw;
    }
}