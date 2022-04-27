package uk.gov.di.authentication.shared.services;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import uk.gov.di.authentication.shared.entity.SPOTCredential;

import java.util.Optional;

public class DynamoSpotService {

    private static final String SPOT_CREDENTIAL_TABLE = "spot-credential";
    private final DynamoDBMapper spotCredentialMapper;
    private final long timeToExist;
    private final AmazonDynamoDB dynamoDB;

    public DynamoSpotService(ConfigurationService configurationService) {
        String region = configurationService.getAwsRegion();
        String environment = configurationService.getEnvironment();
        this.timeToExist = configurationService.getAccessTokenExpiry();
        dynamoDB =
                configurationService
                        .getDynamoEndpointUri()
                        .map(
                                t ->
                                        AmazonDynamoDBClientBuilder.standard()
                                                .withEndpointConfiguration(
                                                        new AwsClientBuilder.EndpointConfiguration(
                                                                t, region)))
                        .orElse(AmazonDynamoDBClientBuilder.standard().withRegion(region))
                        .build();
        DynamoDBMapperConfig spotResponseConfig =
                new DynamoDBMapperConfig.Builder()
                        .withTableNameOverride(
                                DynamoDBMapperConfig.TableNameOverride.withTableNameReplacement(
                                        environment + "-" + SPOT_CREDENTIAL_TABLE))
                        .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
                        .build();
        this.spotCredentialMapper = new DynamoDBMapper(dynamoDB, spotResponseConfig);
        warmUp(environment + "-" + SPOT_CREDENTIAL_TABLE);
    }

    public void addSpotResponse(String subjectID, String serializedCredential) {
        var spotCredential =
                new SPOTCredential()
                        .setSubjectID(subjectID)
                        .setSerializedCredential(serializedCredential)
                        .setTimeToExist(timeToExist);

        spotCredentialMapper.save(spotCredential);
    }

    public Optional<SPOTCredential> getSpotCredential(String subjectID) {
        return Optional.ofNullable(spotCredentialMapper.load(SPOTCredential.class, subjectID));
    }

    public void removeSpotCredential(String subjectID) {
        spotCredentialMapper.delete(spotCredentialMapper.load(SPOTCredential.class, subjectID));
    }

    private void warmUp(String tableName) {
        dynamoDB.describeTable(tableName);
    }
}
