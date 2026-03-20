package store;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

public class SeenJobsStore {

    private static final String TABLE_NAME = "seen-jobs";
    private static final int TTL_DAYS = 7;

    private final DynamoDbClient dynamoDbClient;

    public SeenJobsStore(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    public boolean hasBeenSeen(String jobId) {
        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("id", AttributeValue.fromS(jobId)))
                .build();

        GetItemResponse response = dynamoDbClient.getItem(request);
        return response.hasItem();
    }

    public void markAsSeen(String jobId) {
        long ttl = Instant.now().plus(TTL_DAYS, ChronoUnit.DAYS).getEpochSecond();

        PutItemRequest request = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(Map.of(
                        "id",  AttributeValue.fromS(jobId),
                        "ttl", AttributeValue.fromN(String.valueOf(ttl))
                ))
                .build();

        dynamoDbClient.putItem(request);
    }
}