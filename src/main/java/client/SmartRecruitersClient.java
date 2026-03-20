package client;

import exception.SlugNotFoundException;
import model.Job;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SmartRecruitersClient implements JobBoardClient {

    private static final String BASE_URL = "https://api.smartrecruiters.com/v1/companies/%s/postings";

    private final ObjectMapper objectMapper;

    public SmartRecruitersClient() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL;
    }

    @Override
    public List<Job> fetchJobsForCompany(String slug) throws Exception {
        String releasedAfter = ZonedDateTime.now()
                .minusHours(25)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        String url = String.format(BASE_URL, slug) + "?releasedAfter=" + releasedAfter + "&limit=100";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            throw new SlugNotFoundException(slug);
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("Unexpected status code: " + response.statusCode());
        }

        return parseJobs(response.body(), toDisplayName(slug));
    }

    @Override
    public List<Job> parseJobs(String responseBody, String companyName) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode contentArray = root.get("content");

        if (contentArray == null || !contentArray.isArray()) {
            return Collections.emptyList();
        }

        List<Job> jobs = new ArrayList<>();

        for (JsonNode jobNode : contentArray) {
            String id = jobNode.get("id").asString();
            String title = jobNode.get("name").asString();
            String company = jobNode.path("company").path("name").asString(companyName);
            String location = jobNode.path("location").path("fullLocation").asString(null);
            String releasedDate = jobNode.path("releasedDate").asString(null);
            String companyIdentifier = jobNode.path("company").path("identifier").asString(null);
            String url = buildJobUrl(companyIdentifier, id);

            jobs.add(new Job(id, title, company, location, releasedDate, url));
        }

        return jobs;
    }

    private String buildJobUrl(String companyIdentifier, String id) {
        if (companyIdentifier == null || id == null) return null;
        return "https://jobs.smartrecruiters.com/" + companyIdentifier + "/" + id;
    }
}