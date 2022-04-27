package uk.gov.di.authentication.shared.services;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.nimbusds.oauth2.sdk.id.ClientID;
import uk.gov.di.authentication.shared.entity.ClientRegistry;
import uk.gov.di.authentication.shared.entity.UpdateClientConfigRequest;
import uk.gov.di.authentication.shared.helpers.IdGenerator;

import java.util.List;
import java.util.Optional;

public class DynamoClientService implements ClientService {

    private static final String CLIENT_REGISTRY_TABLE = "client-registry";
    private final DynamoDBMapper clientRegistryMapper;
    private final AmazonDynamoDB dynamoDB;

    public DynamoClientService(ConfigurationService configurationService) {
        String region = configurationService.getAwsRegion();
        String tableName = configurationService.getEnvironment() + "-" + CLIENT_REGISTRY_TABLE;
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

        DynamoDBMapperConfig clientRegistryConfig =
                new DynamoDBMapperConfig.Builder()
                        .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
                        .withTableNameOverride(
                                DynamoDBMapperConfig.TableNameOverride.withTableNameReplacement(
                                        tableName))
                        .build();

        this.clientRegistryMapper = new DynamoDBMapper(dynamoDB, clientRegistryConfig);
        warmUp(tableName);
    }

    @Override
    public boolean isValidClient(String clientId) {
        return clientRegistryMapper.load(ClientRegistry.class, clientId) != null;
    }

    @Override
    public void addClient(
            String clientID,
            String clientName,
            List<String> redirectUris,
            List<String> contacts,
            List<String> scopes,
            String publicKey,
            List<String> postLogoutRedirectUris,
            String backChannelLogoutUri,
            String serviceType,
            String sectorIdentifierUri,
            String subjectType,
            boolean consentRequired,
            List<String> claims,
            String clientType) {
        var clientRegistry =
                new ClientRegistry()
                        .setClientID(clientID)
                        .setClientName(clientName)
                        .setRedirectUrls(redirectUris)
                        .setContacts(contacts)
                        .setScopes(scopes)
                        .setPublicKey(publicKey)
                        .setPostLogoutRedirectUrls(postLogoutRedirectUris)
                        .setBackChannelLogoutUri(backChannelLogoutUri)
                        .setServiceType(serviceType)
                        .setSectorIdentifierUri(sectorIdentifierUri)
                        .setSubjectType(subjectType)
                        .setConsentRequired(consentRequired)
                        .setClaims(claims)
                        .setClientType(clientType);
        clientRegistryMapper.save(clientRegistry);
    }

    @Override
    public ClientRegistry updateClient(String clientId, UpdateClientConfigRequest updateRequest) {
        ClientRegistry clientRegistry = clientRegistryMapper.load(ClientRegistry.class, clientId);
        Optional.ofNullable(updateRequest.getRedirectUris())
                .ifPresent(clientRegistry::setRedirectUrls);
        Optional.ofNullable(updateRequest.getClientName()).ifPresent(clientRegistry::setClientName);
        Optional.ofNullable(updateRequest.getContacts()).ifPresent(clientRegistry::setContacts);
        Optional.ofNullable(updateRequest.getScopes()).ifPresent(clientRegistry::setScopes);
        Optional.ofNullable(updateRequest.getPostLogoutRedirectUris())
                .ifPresent(clientRegistry::setPostLogoutRedirectUrls);
        Optional.ofNullable(updateRequest.getPublicKey()).ifPresent(clientRegistry::setPublicKey);
        Optional.ofNullable(updateRequest.getServiceType())
                .ifPresent(clientRegistry::setServiceType);
        Optional.ofNullable(updateRequest.getSectorIdentifierUri())
                .ifPresent(clientRegistry::setSectorIdentifierUri);
        clientRegistryMapper.save(clientRegistry);
        return clientRegistry;
    }

    @Override
    public Optional<ClientRegistry> getClient(String clientId) {
        return Optional.ofNullable(clientRegistryMapper.load(ClientRegistry.class, clientId));
    }

    @Override
    public ClientID generateClientID() {
        return new ClientID(IdGenerator.generate());
    }

    private void warmUp(String tableName) {
        dynamoDB.describeTable(tableName);
    }
}
