package service;

import model.ClientConfig;
import model.FetchResult;
import model.Job;
import notification.DiscordNotifier;
import store.SeenJobsStore;
import store.SlugHealthStore;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class JobAlertService {

    private final List<ClientConfig> configs;
    private final SeenJobsStore seenJobsStore;
    private final SlugHealthStore slugHealthStore;
    private final DiscordNotifier discordNotifier;

    public JobAlertService(List<ClientConfig> configs, SeenJobsStore seenJobsStore, SlugHealthStore slugHealthStore, DiscordNotifier discordNotifier) {
        this.configs = configs;
        this.seenJobsStore = seenJobsStore;
        this.slugHealthStore = slugHealthStore;
        this.discordNotifier = discordNotifier;
    }

    public void run() throws Exception {
        FetchResult combinedResult = pollAllClients();
        updateSlugHealth(combinedResult);
        processNewJobs(combinedResult.jobs());
    }

    private FetchResult pollAllClients() {
        List<Job> allJobs = new ArrayList<>();
        List<String> allNotFoundSlugs = new ArrayList<>();
        List<String> allSuccessfulSlugs = new ArrayList<>();

        for (ClientConfig config : configs) {
            List<String> activeSlugs = config.slugs().stream()
                    .filter(slug -> !slugHealthStore.isSuppressed(slug))
                    .toList();

            FetchResult result = config.client().fetchAllJobs(activeSlugs);
            allJobs.addAll(result.jobs());
            allNotFoundSlugs.addAll(result.notFoundSlugs());

            List<String> successfulSlugs = activeSlugs.stream()
                    .filter(slug -> !result.notFoundSlugs().contains(slug))
                    .toList();
            allSuccessfulSlugs.addAll(successfulSlugs);
        }

        System.out.printf("Fetched %d total jobs across %d clients%n", allJobs.size(), configs.size());

        return new FetchResult(allJobs, allNotFoundSlugs, allSuccessfulSlugs);
    }

    private void updateSlugHealth(FetchResult result) throws Exception {
        for (String slug : result.successfulSlugs()) {
            slugHealthStore.recordSuccess(slug);
        }

        if (result.notFoundSlugs().isEmpty()) return;

        List<String> slugsToAlert = new ArrayList<>();
        for (String slug : result.notFoundSlugs()) {
            slugHealthStore.recordFailure(slug);
            if (slugHealthStore.getFailureCount(slug) >= slugHealthStore.getFailureThreshold()) {
                slugHealthStore.suppress(slug);
                slugsToAlert.add(slug);
            }
        }

        if (!slugsToAlert.isEmpty()) {
            discordNotifier.notifyDeadSlugs(slugsToAlert);
        }
    }

    private void processNewJobs(List<Job> allJobs) throws Exception {
        List<Job> newJobs = new ArrayList<>();
        for (Job job : allJobs) {
            if (isRecent(job) && !seenJobsStore.hasBeenSeen(job.id())) {
                newJobs.add(job);
            }
        }
        System.out.printf("%d new job(s) found%n", newJobs.size());

        if (newJobs.isEmpty()) return;

        discordNotifier.notify(newJobs);

        for (Job job : newJobs) {
            seenJobsStore.markAsSeen(job.id());
        }
        System.out.printf("Marked %d job(s) as seen%n", newJobs.size());
    }

    private boolean isRecent(Job job) {
        if (job.firstPublished() == null) return false;
        try {
            ZonedDateTime published = ZonedDateTime.parse(job.firstPublished());
            return published.isAfter(ZonedDateTime.now().minusDays(1));
        } catch (Exception e) {
            try {
                LocalDate date = LocalDate.parse(job.firstPublished());
                return !date.isBefore(LocalDate.now().minusDays(1));
            } catch (Exception e2) {
                return false;
            }
        }
    }
}