package notification;

import model.Job;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class DiscordNotifier {

    private static final Set<String> CITY_KEYWORDS = Set.of(
            "new york", "manhattan", "brooklyn", "queens", "bronx", "staten island",
            "long island city", "newark", "new jersey", "hoboken", "jersey city", "nj", "connecticut", "ct",
            "stamford", "greenwich", "westchester", "district of columbia", "washington, d.c.", "d.c.",
            "washington dc", "arlington", "alexandria", "remote, usa", "remote, us", "remote - united states", "remote - us",
            "philadelphia", "pittsburgh", "chicago", "minneapolis", "saint paul", "st. paul"
    );
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String webhookUrl;

    public DiscordNotifier(String webhookUrl) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.webhookUrl = webhookUrl;
    }

    public void notify(List<Job> newJobs) throws Exception {
        if (newJobs.isEmpty()) {
            System.out.println("No new jobs to notify.");
            return;
        }

        List<String> messages = formatJobMessages(newJobs);
        for (String message : messages) {
            sendMessage(message);
            Thread.sleep(1000);
        }
    }

    public void notifyDeadSlugs(List<String> slugs) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ **Dead slug alert — polling suspended for the following slugs:**\n\n");

        for (String slug : slugs) {
            sb.append("• `").append(slug).append("`\n");
        }

        sb.append("\nThese slugs have returned 404 ")
                .append(slugs.size() == 1 ? "three times in a row" : "three times in a row each")
                .append(" and may have left their respective ATS. Remove them from the slug list or investigate.");

        sendMessage(sb.toString());
    }

    private boolean isDesiredLocation(String location) {
        if (location == null || location.isBlank()) return false;

        String lower = location.toLowerCase();

        if (lower.equals("remote")) return false;

        return CITY_KEYWORDS.stream().anyMatch(keyword -> {
            String prefix = Character.isLetterOrDigit(keyword.charAt(0)) ? "\\b" : "";
            String suffix = Character.isLetterOrDigit(keyword.charAt(keyword.length() - 1)) ? "\\b" : "";
            return lower.matches(".*" + prefix + Pattern.quote(keyword) + suffix + ".*");
        });
    }

    private String formatTimestamp(String raw) {
        if (raw == null) return "Unknown";

        // Full ISO 8601 timestamp (Greenhouse, Lever, Ashby, SmartRecruiters)
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(raw);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z");
            return zdt.withZoneSameInstant(ZoneId.of("America/New_York")).format(formatter);
        } catch (Exception ignored) {}

        // Date-only string (Workable's published_on e.g. "2026-03-04")
        try {
            LocalDate date = LocalDate.parse(raw);
            return date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"));
        } catch (Exception ignored) {}

        System.out.printf("Unrecognized timestamp format for value '%s', returning as-is%n", raw);
        return raw;
    }

    private List<String> formatJobMessages(List<Job> jobs) {
        List<String> messages = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        for (Job job : jobs) {
            if (!isDesiredLocation(job.location())) {
                continue;
            }

            String entry = "**" + job.companyName() + "** — " + job.title() + "\n" +
                    "📍 " + job.location() + "\n" +
                    "🗓️ Posted: " + formatTimestamp(job.firstPublished()) + "\n" +
                    "🔗 <" + job.url() + ">" + "\n\n";

            if (sb.length() + entry.length() > 1900) {
                messages.add(sb.toString());
                sb = new StringBuilder();
            }

            sb.append(entry);
        }

        if (!sb.isEmpty()) {
            messages.add(sb.toString());
        }

        return messages;
    }

    private void sendMessage(String content) throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of("content", content));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 204) {
            throw new RuntimeException("Discord webhook failed with status: " + response.statusCode());
        }
    }
}