package uk.gov.di.authentication.utils;

import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.nimbusds.oauth2.sdk.id.Subject;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.di.authentication.shared.entity.BulkEmailStatus;
import uk.gov.di.authentication.shared.serialization.Json;
import uk.gov.di.authentication.shared.services.BulkEmailUsersService;
import uk.gov.di.authentication.shared.services.LambdaInvokerService;
import uk.gov.di.authentication.shared.services.SystemService;
import uk.gov.di.authentication.sharedtest.basetest.HandlerIntegrationTest;
import uk.gov.di.authentication.sharedtest.extensions.BulkEmailUsersExtension;
import uk.gov.di.authentication.utils.lambda.BulkUserEmailAudienceLoaderScheduledEventHandler;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static java.lang.String.valueOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkUserEmailAudienceLoaderScheduledEventHandlerIntegrationTest
        extends HandlerIntegrationTest<ScheduledEvent, Void> {

    @RegisterExtension
    protected static final BulkEmailUsersExtension bulkEmailUsersExtension =
            new BulkEmailUsersExtension();

    private BulkEmailUsersService bulkEmailUsersService;

    private Long bulkUserEmailAudienceLoadUserBatchSize = 5L;

    private Long bulkUserEmailMaxAudienceLoadUserCount = 24L;

    @BeforeEach
    void setup() {
        var configuration =
                new IntegrationTestConfigurationService(
                        auditTopic,
                        notificationsQueue,
                        auditSigningKey,
                        tokenSigner,
                        ipvPrivateKeyJwtSigner,
                        spotQueue,
                        docAppPrivateKeyJwtSigner,
                        configurationParameters,
                        new SystemService()) {

                    @Override
                    public String getTxmaAuditQueueUrl() {
                        return txmaAuditQueue.getQueueUrl();
                    }

                    @Override
                    public int getBulkUserEmailBatchQueryLimit() {
                        return 3;
                    }

                    @Override
                    public long getBulkUserEmailAudienceLoadUserBatchSize() {
                        return bulkUserEmailAudienceLoadUserBatchSize;
                    }

                    @Override
                    public long getBulkUserEmailMaxAudienceLoadUserCount() {
                        return bulkUserEmailMaxAudienceLoadUserCount;
                    }

                    @Override
                    public boolean isBulkUserEmailEmailSendingEnabled() {
                        return true;
                    }
                };

        handler = new BulkUserEmailAudienceLoaderScheduledEventHandler(configuration);
        var lambdaInvokerService =
                new LambdaInvokerService(configuration, null) {
                    @Override
                    public void invokeWithPayload(ScheduledEvent scheduledEvent) {
                        handler.handleRequest(scheduledEvent, context);
                    }
                };

        ((BulkUserEmailAudienceLoaderScheduledEventHandler) handler)
                .setLambdaInvoker(lambdaInvokerService);

        bulkEmailUsersService = new BulkEmailUsersService(configuration);
    }

    @Test
    void shouldLoadSingleUserFromUserProfile() throws Json.JsonException {

        userStore.signUp("user.1@account.gov.uk", "password123", new Subject("1"));

        makeRequest(Optional.empty());

        var usersLoaded = bulkEmailUsersService.getNSubjectIdsByStatus(10, BulkEmailStatus.PENDING);

        assertThat(usersLoaded.get(0), equalTo("1"));
        assertThat(usersLoaded.size(), equalTo(1));
    }

    @Test
    void shouldLoadMultipleUsersFromUserProfileWhenBatchSizeLowerThanUsers() {
        final int numberOfUsers = bulkUserEmailAudienceLoadUserBatchSize.intValue() + 10;
        setupDynamo(numberOfUsers);
        makeRequest(Optional.empty());

        var usersLoaded =
                bulkEmailUsersService.getNSubjectIdsByStatus(
                        numberOfUsers + 1, BulkEmailStatus.PENDING);

        assertThat(usersLoaded.size(), equalTo(numberOfUsers));
        for (int i = 1; i <= usersLoaded.size(); i++) {
            assertTrue(usersLoaded.contains(valueOf(i)));
        }
    }

    @Test
    void shouldLoadMultipleUsersFromUserProfileWhenBatchSizeHigherThanUsers() {
        final int numberOfUsers = 7;
        setupDynamo(numberOfUsers);
        makeRequest(Optional.empty());

        var usersLoaded =
                bulkEmailUsersService.getNSubjectIdsByStatus(
                        numberOfUsers + 1, BulkEmailStatus.PENDING);

        assertThat(usersLoaded.size(), equalTo(numberOfUsers));
        for (int i = 1; i <= usersLoaded.size(); i++) {
            assertTrue(usersLoaded.contains(valueOf(i)));
        }
    }

    @Test
    void shouldGetVerifiedUsersWithoutTheExcludedTermsAndConditionsVersions() {
        // Excluded terms and conditions are set as the
        // BULK_USER_EMAIL_INCLUDED_TERMS_AND_CONDITIONS
        // environment variable
        setupDynamoUsersWithTermsAndConditionsVersions();
        makeRequest(Optional.empty());

        var usersLoaded = bulkEmailUsersService.getNSubjectIdsByStatus(15, BulkEmailStatus.PENDING);

        assertThat(usersLoaded.size(), equalTo(7));
    }

    @Test
    void shouldOnlyLoadUsersUpToTheMaxLimit() {
        final Integer numberOfUsers = bulkUserEmailMaxAudienceLoadUserCount.intValue() + 10;
        setupDynamo(numberOfUsers);
        makeRequest(Optional.empty());

        var usersLoaded =
                bulkEmailUsersService.getNSubjectIdsByStatus(
                        numberOfUsers + 1, BulkEmailStatus.PENDING);

        assertThat(usersLoaded.size(), equalTo(bulkUserEmailMaxAudienceLoadUserCount.intValue()));
    }

    private void setupDynamo(int numberOfUsers) {
        for (int i = 1; i <= numberOfUsers; i++) {
            userStore.signUp(
                    format("user.%o@account.gov.uk", i), "password123", new Subject(valueOf(i)));
        }
    }

    private void setupDynamoUsersWithTermsAndConditionsVersions() {
        userStore.signUp("email0", "password-1", new Subject("0000"), "1.0");
        userStore.signUp("email1", "password-1", new Subject("1111"), "1.0");
        userStore.signUp("email2", "password-1", new Subject("2222"), "1.0");
        userStore.signUp("email3", "password-1", new Subject("3333"), "1.1");
        userStore.signUp("email4", "password-1", new Subject("4444"), "1.2");
        userStore.signUp("email5", "password-1", new Subject("5555"), "1.2");
        userStore.signUp("email6", "password-1", new Subject("6666"), "1.2");
        userStore.signUp("email7", "password-1", new Subject("7777"), "1.5");
        userStore.signUp("email8", "password-1", new Subject("8888"), "1.5");
        userStore.signUp("email9", "password-1", new Subject("9999"), "1.6");
        userStore.signUp("email10", "password-1", new Subject("A0000"), null);
        userStore.signUp("email11", "password-1", new Subject("A1111"), null);
        userStore.addUnverifiedUser("email12", "password-1", new Subject("A2222"), "1.3");
        userStore.addUnverifiedUser("email13", "password-1", new Subject("A3333"), "1.3");
        userStore.addUnverifiedUser("email14", "password-1", new Subject("A4444"), "1.3");
    }

    private void makeRequest(Optional<Map<String, Object>> exclusiveStartKey) {
        var detail = exclusiveStartKey.orElse(Map.of());
        ScheduledEvent scheduledEvent =
                new ScheduledEvent()
                        .withAccount("12345678")
                        .withRegion("eu-west-2")
                        .withDetailType("Scheduled Event")
                        .withDetail(detail)
                        .withSource("aws.events")
                        .withId("abcd-1234-defg-5678")
                        .withTime(DateTime.now())
                        .withResources(
                                List.of(
                                        "arn:aws:events:eu-west-2:12345678:rule/email-campaign-audience-load-rule"));

        handler.handleRequest(scheduledEvent, context);
    }
}