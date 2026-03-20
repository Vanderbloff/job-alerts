package model;

import client.JobBoardClient;

import java.util.List;

public record ClientConfig(
        JobBoardClient client,
        List<String> slugs
) {}