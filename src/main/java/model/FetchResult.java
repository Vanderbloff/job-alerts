package model;

import java.util.List;

public record FetchResult(
        List<Job> jobs,
        List<String> notFoundSlugs,
        List<String> successfulSlugs
) {}