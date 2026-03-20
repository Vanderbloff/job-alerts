package client;

import exception.SlugNotFoundException;
import model.FetchResult;
import model.Job;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public interface JobBoardClient {

    HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    String getBaseUrl();

    default FetchResult fetchAllJobs(List<String> slugs) {
        List<Job> allJobs = new ArrayList<>();
        List<String> notFoundSlugs = new ArrayList<>();
        List<String> successfulSlugs = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<FetchResult>> futures = slugs.stream()
                    .map(slug -> executor.submit(() -> {
                        try {
                            List<Job> jobs = fetchJobsForCompany(slug);
                            return new FetchResult(jobs, List.of(), List.of(slug));
                        } catch (SlugNotFoundException e) {
                            return new FetchResult(List.of(), List.of(slug), List.of());
                        } catch (Exception e) {
                            System.err.printf("Failed to fetch jobs for slug '%s': %s%n", slug, e.getMessage());
                            return new FetchResult(List.of(), List.of(), List.of());
                        }
                    }))
                    .toList();

            for (Future<FetchResult> future : futures) {
                try {
                    FetchResult result = future.get();
                    allJobs.addAll(result.jobs());
                    notFoundSlugs.addAll(result.notFoundSlugs());
                    successfulSlugs.addAll(result.successfulSlugs());
                } catch (Exception e) {
                    System.err.println("Unexpected error collecting future: " + e.getMessage());
                }
            }

        }

        return new FetchResult(allJobs, notFoundSlugs, successfulSlugs);
    }

    default List<Job> fetchJobsForCompany(String slug) throws Exception {
        String url = String.format(getBaseUrl(), slug);

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

    List<Job> parseJobs(String responseBody, String companyName) throws Exception;

    default String toDisplayName(String slug) {
        if (slug == null || slug.isEmpty()) return slug;
        return Character.toUpperCase(slug.charAt(0)) + slug.substring(1);
    }
}