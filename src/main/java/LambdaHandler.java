import client.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import model.ClientConfig;
import notification.DiscordNotifier;
import service.JobAlertService;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import store.SeenJobsStore;
import store.SlugHealthStore;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class LambdaHandler implements RequestHandler<Map<String, Object>, Void> {

    private final JobAlertService jobAlertService;

    public LambdaHandler() {
        DynamoDbClient dynamoDbClient = DynamoDbClient.create();

        try (SsmClient ssmClient = SsmClient.create()) {
            String webhookUrl = fetchWebhookUrl(ssmClient);
            List<ClientConfig> configs = buildConfigs(ssmClient);

            SeenJobsStore seenJobsStore = new SeenJobsStore(dynamoDbClient);
            SlugHealthStore slugHealthStore = new SlugHealthStore(dynamoDbClient);
            DiscordNotifier discordNotifier = new DiscordNotifier(webhookUrl);

            this.jobAlertService = new JobAlertService(configs, seenJobsStore, slugHealthStore, discordNotifier);
        }
        catch (Exception e) {
            System.err.println("Failed to load configuration from SSM — either the parameter is missing or the slug JSON is malformed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private String fetchWebhookUrl(SsmClient ssmClient) {
        GetParameterResponse response = ssmClient.getParameter(
                GetParameterRequest.builder()
                        .name("/job-alerts/discord-webhook-url")
                        .withDecryption(true)
                        .build()
        );
        return response.parameter().value();
    }

    private List<ClientConfig> buildConfigs(SsmClient ssmClient) throws Exception {
        GetParameterResponse response = ssmClient.getParameter(
                GetParameterRequest.builder()
                        .name("/job-alerts/slugs")
                        .withDecryption(false)
                        .build()
        );

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, List<String>> slugMap = objectMapper.readValue(
                response.parameter().value(),
                new TypeReference<>() {}
        );

        return List.of(
                new ClientConfig(new GreenhouseClient(), slugMap.getOrDefault("greenhouse", List.of())),
                new ClientConfig(new LeverClient(), slugMap.getOrDefault("lever", List.of())),
                new ClientConfig(new AshbyClient(), slugMap.getOrDefault("ashby", List.of())),
                new ClientConfig(new SmartRecruitersClient(), slugMap.getOrDefault("smartrecruiters", List.of())),
                new ClientConfig(new WorkableClient(), slugMap.getOrDefault("workable", List.of()))
        );
    }

    @Override
    public Void handleRequest(Map<String, Object> event, Context context) {
        try {
            jobAlertService.run();
        } catch (Exception e) {
            System.err.println("Job alert run failed: " + e.getMessage());
            throw new RuntimeException(e);
        }

        return null;
    }
}