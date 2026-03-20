package store;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.Map;

public class SlugHealthStore {

    private static final String TABLE_NAME = "slug-health";
    private static final int FAILURE_THRESHOLD = 3;

    private final DynamoDbClient dynamoDbClient;

    public SlugHealthStore(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    public int getFailureCount(String slug) {
        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("slug", AttributeValue.fromS(slug)))
                .build());

        if (!response.hasItem()) return 0;

        AttributeValue failures = response.item().get("consecutiveFailures");
        return failures != null ? Integer.parseInt(failures.n()) : 0;
    }

    public boolean isSuppressed(String slug) {
        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("slug", AttributeValue.fromS(slug)))
                .build());

        if (!response.hasItem()) return false;

        AttributeValue suppressed = response.item().get("suppressed");
        return suppressed != null && Boolean.parseBoolean(suppressed.s());
    }

    public void recordFailure(String slug) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("slug", AttributeValue.fromS(slug)))
                .updateExpression("SET consecutiveFailures = if_not_exists(consecutiveFailures, :zero) + :inc")
                .expressionAttributeValues(Map.of(
                        ":zero", AttributeValue.fromN("0"),
                        ":inc",  AttributeValue.fromN("1")
                ))
                .build());
    }

    public void recordSuccess(String slug) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("slug", AttributeValue.fromS(slug)))
                .updateExpression("SET consecutiveFailures = :zero")
                .expressionAttributeValues(Map.of(
                        ":zero", AttributeValue.fromN("0")
                ))
                .build());
    }

    public void suppress(String slug) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("slug", AttributeValue.fromS(slug)))
                .updateExpression("SET suppressed = :true")
                .expressionAttributeValues(Map.of(
                        ":true", AttributeValue.fromS("true")
                ))
                .build());
    }

    public int getFailureThreshold() {
        return FAILURE_THRESHOLD;
    }
}