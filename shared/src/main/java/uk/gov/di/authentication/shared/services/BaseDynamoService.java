package uk.gov.di.authentication.shared.services;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.Optional;

import static uk.gov.di.authentication.shared.dynamodb.DynamoClientHelper.createDynamoClient;

public class BaseDynamoService<T> {

    private final DynamoDbTable<T> dynamoTable;
    private final DynamoDbClient client;

    public BaseDynamoService(
            Class<T> objectClass, String table, ConfigurationService configurationService) {
        var tableName = configurationService.getEnvironment() + "-" + table;

        client = createDynamoClient(configurationService);
        var enhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
        dynamoTable = enhancedClient.table(tableName, TableSchema.fromBean(objectClass));

        warmUp();
    }

    public void update(T item) {
        dynamoTable.updateItem(item);
    }

    public void put(T item) {
        dynamoTable.putItem(item);
    }

    public Optional<T> get(String partition) {
        return Optional.ofNullable(
                dynamoTable.getItem(Key.builder().partitionValue(partition).build()));
    }

    public void delete(String partition) {
        get(partition).ifPresent(dynamoTable::deleteItem);
    }

    private void warmUp() {
        dynamoTable.describeTable();
    }

    public QueryResponse query(QueryRequest request) {
        return client.query(request);
    }
}
