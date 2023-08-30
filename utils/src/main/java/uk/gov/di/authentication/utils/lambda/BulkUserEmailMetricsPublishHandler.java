package uk.gov.di.authentication.utils.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import uk.gov.di.authentication.shared.services.CloudwatchMetricsService;
import uk.gov.di.authentication.shared.services.ConfigurationService;
import uk.gov.di.authentication.shared.services.DynamoService;

import java.util.List;

import static java.text.MessageFormat.format;
import static uk.gov.di.authentication.shared.dynamodb.DynamoClientHelper.createDynamoClient;

public class BulkUserEmailMetricsPublishHandler implements RequestHandler<ScheduledEvent, Long> {

    private static final Logger LOG =
            LogManager.getLogger(BulkUserEmailSenderScheduledEventHandler.class);

    private final ConfigurationService configurationService;
    private final DynamoDbClient client;
    private final CloudwatchMetricsService cloudwatchMetricsService;
    private final DynamoService dynamoService;

    public BulkUserEmailMetricsPublishHandler(
            ConfigurationService configurationService,
            DynamoDbClient client,
            CloudwatchMetricsService cloudwatchMetricsService,
            DynamoService dynamoService) {
        this.configurationService = configurationService;
        this.client = client;
        this.cloudwatchMetricsService = cloudwatchMetricsService;
        this.dynamoService = dynamoService;
    }

    public BulkUserEmailMetricsPublishHandler() {
        this.configurationService = ConfigurationService.getInstance();
        client = createDynamoClient(configurationService);
        cloudwatchMetricsService = new CloudwatchMetricsService();
        this.dynamoService = new DynamoService(configurationService);
    }

    @Override
    public Long handleRequest(ScheduledEvent input, Context context) {
        var result =
                client.describeTable(
                        DescribeTableRequest.builder()
                                .tableName(
                                        format(
                                                "{0}-user-profile",
                                                configurationService.getEnvironment()))
                                .build());
        var numberOfAccounts = result.table().itemCount();
        var numberOfVerifiedAccounts =
                result.table().globalSecondaryIndexes().stream()
                        .filter(i -> i.indexName().equals("VerifiedAccountIndex"))
                        .findFirst()
                        .orElseThrow()
                        .itemCount();

        var numberOfAccountsV11 =
                dynamoService.getUserCountForVerifiedAccountsOnTermsAndConditionsVersion(
                        List.of("1.1"));
        var numberOfAccountsV12 =
                dynamoService.getUserCountForVerifiedAccountsOnTermsAndConditionsVersion(
                        List.of("1.2"));

        //        cloudwatchMetricsService.putEmbeddedValue(
        //                "BulkEmailNumberOfAccounts", numberOfAccounts, Map.of());
        //        cloudwatchMetricsService.putEmbeddedValue(
        //                "BulkEmailNumberOfVerifiedAccounts", numberOfVerifiedAccounts, Map.of());

        LOG.info(
                "BulkEmailNumberOfAccounts: {}, BulkEmailNumberOfVerifiedAccounts: {}, NumberOfAccountsV11: {}, NumberOfAccountsV12: {}",
                numberOfAccounts,
                numberOfVerifiedAccounts,
                numberOfAccountsV11,
                numberOfAccountsV12);

        return numberOfVerifiedAccounts;
    }
}
