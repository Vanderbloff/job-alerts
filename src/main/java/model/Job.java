package model;

public record Job(
        String id,
        String title,
        String companyName,
        String location,
        String firstPublished,
        String url
) {}
