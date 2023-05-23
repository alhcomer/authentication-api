package uk.gov.di.accountmanagement.api;

import com.nimbusds.oauth2.sdk.id.Subject;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.di.accountmanagement.entity.NotifyRequest;
import uk.gov.di.accountmanagement.entity.UpdateEmailRequest;
import uk.gov.di.accountmanagement.lambda.UpdateEmailHandler;
import uk.gov.di.authentication.shared.entity.ErrorResponse;
import uk.gov.di.authentication.shared.helpers.ClientSubjectHelper;
import uk.gov.di.authentication.shared.helpers.LocaleHelper.SupportedLanguage;
import uk.gov.di.authentication.sharedtest.basetest.ApiGatewayHandlerIntegrationTest;
import uk.gov.di.authentication.sharedtest.helper.AuditAssertionsHelper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.di.accountmanagement.domain.AccountManagementAuditableEvent.UPDATE_EMAIL;
import static uk.gov.di.accountmanagement.entity.NotificationType.EMAIL_UPDATED;
import static uk.gov.di.accountmanagement.testsupport.helpers.NotificationAssertionHelper.assertNoNotificationsReceived;
import static uk.gov.di.accountmanagement.testsupport.helpers.NotificationAssertionHelper.assertNotificationsReceived;
import static uk.gov.di.authentication.sharedtest.helper.AuditAssertionsHelper.assertTxmaAuditEventsReceived;
import static uk.gov.di.authentication.sharedtest.matchers.APIGatewayProxyResponseEventMatcher.hasBody;
import static uk.gov.di.authentication.sharedtest.matchers.APIGatewayProxyResponseEventMatcher.hasStatus;

class UpdateEmailIntegrationTest extends ApiGatewayHandlerIntegrationTest {

    private static final String EXISTING_EMAIL_ADDRESS = "joe.bloggs@digital.cabinet-office.gov.uk";
    private static final String NEW_EMAIL_ADDRESS = "joe.b@digital.cabinet-office.gov.uk";
    private static final Subject SUBJECT = new Subject();
    private static final String INTERNAl_SECTOR_HOST = "test.account.gov.uk";

    @BeforeEach
    void setup() {
        handler = new UpdateEmailHandler(TXMA_ENABLED_CONFIGURATION_SERVICE);
        txmaAuditQueue.clear();
    }

    @Test
    void shouldCallUpdateEmailEndpointAndReturn204WhenUpdatingEmailIsSuccessful() {
        var internalCommonSubId = setupUserAndRetrieveInternalCommonSubId();
        var otp = redis.generateAndSaveEmailCode(NEW_EMAIL_ADDRESS, 300);
        var response =
                makeRequest(
                        Optional.of(
                                new UpdateEmailRequest(
                                        EXISTING_EMAIL_ADDRESS, NEW_EMAIL_ADDRESS, otp)),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Map.of("principalId", internalCommonSubId));

        assertThat(response, hasStatus(HttpStatus.SC_NO_CONTENT));
        assertThat(userStore.getEmailForUser(SUBJECT), is(NEW_EMAIL_ADDRESS));

        assertNotificationsReceived(
                notificationsQueue,
                List.of(
                        new NotifyRequest(
                                EXISTING_EMAIL_ADDRESS, EMAIL_UPDATED, SupportedLanguage.EN),
                        new NotifyRequest(NEW_EMAIL_ADDRESS, EMAIL_UPDATED, SupportedLanguage.EN)));

        assertTxmaAuditEventsReceived(txmaAuditQueue, List.of(UPDATE_EMAIL));
    }

    @Test
    void shouldReturn400WhenOtpIsInvalid() throws Exception {
        var internalCommonSubId = setupUserAndRetrieveInternalCommonSubId();
        String validOtp = redis.generateAndSaveEmailCode(NEW_EMAIL_ADDRESS, 300);
        var badOtp = "012345";

        var response =
                makeRequest(
                        Optional.of(
                                new UpdateEmailRequest(
                                        EXISTING_EMAIL_ADDRESS, NEW_EMAIL_ADDRESS, badOtp)),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Map.of("principalId", internalCommonSubId));

        assertThat(response, hasStatus(HttpStatus.SC_BAD_REQUEST));
        assertThat(response, hasBody(objectMapper.writeValueAsString(ErrorResponse.ERROR_1020)));

        assertNoNotificationsReceived(notificationsQueue);

        AuditAssertionsHelper.assertNoTxmaAuditEventsReceived(txmaAuditQueue);
    }

    @Test
    void shouldReturn400WhenNewEmailIsMalformed() {
        var badEmailAddress = "This is not a valid email address";
        var internalCommonSubId = setupUserAndRetrieveInternalCommonSubId();
        var otp = redis.generateAndSaveEmailCode(NEW_EMAIL_ADDRESS, 300);

        var response =
                makeRequest(
                        Optional.of(
                                new UpdateEmailRequest(
                                        EXISTING_EMAIL_ADDRESS, badEmailAddress, otp)),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Map.of("principalId", internalCommonSubId));

        assertThat(response, hasStatus(HttpStatus.SC_BAD_REQUEST));

        assertNoNotificationsReceived(notificationsQueue);

        AuditAssertionsHelper.assertNoTxmaAuditEventsReceived(txmaAuditQueue);
    }

    @Test
    void shouldReturn400WhenNewEmailIsAlreadyTaken() throws Exception {
        var internalCommonSubId = setupUserAndRetrieveInternalCommonSubId();
        userStore.signUp(NEW_EMAIL_ADDRESS, "password-2", new Subject());
        var otp = redis.generateAndSaveEmailCode(NEW_EMAIL_ADDRESS, 300);

        var response =
                makeRequest(
                        Optional.of(
                                new UpdateEmailRequest(
                                        EXISTING_EMAIL_ADDRESS, NEW_EMAIL_ADDRESS, otp)),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Map.of("principalId", internalCommonSubId));

        assertThat(response, hasStatus(HttpStatus.SC_BAD_REQUEST));
        assertThat(response, hasBody(objectMapper.writeValueAsString(ErrorResponse.ERROR_1009)));

        assertNoNotificationsReceived(notificationsQueue);

        AuditAssertionsHelper.assertNoTxmaAuditEventsReceived(txmaAuditQueue);
    }

    @Test
    void shouldThrowExceptionWhenUserAttemptsToUpdateDifferentAccount() {
        var internalCommonSubId = setupUserAndRetrieveInternalCommonSubId();
        userStore.signUp("other.user@digital.cabinet-office.gov.uk", "password-2", new Subject());
        String otp = redis.generateAndSaveEmailCode(NEW_EMAIL_ADDRESS, 300);

        Exception ex =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                makeRequest(
                                        Optional.of(
                                                new UpdateEmailRequest(
                                                        "other.user@digital.cabinet-office.gov.uk",
                                                        NEW_EMAIL_ADDRESS,
                                                        otp)),
                                        Collections.emptyMap(),
                                        Collections.emptyMap(),
                                        Collections.emptyMap(),
                                        Map.of("principalId", internalCommonSubId)));

        assertThat(ex.getMessage(), is("Invalid Principal in request"));
    }

    @Test
    void shouldThrowExceptionWhenSubjectIdMissing() {
        setupUserAndRetrieveInternalCommonSubId();
        String otp = redis.generateAndSaveEmailCode(NEW_EMAIL_ADDRESS, 300);

        Exception ex =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                makeRequest(
                                        Optional.of(
                                                new UpdateEmailRequest(
                                                        EXISTING_EMAIL_ADDRESS,
                                                        NEW_EMAIL_ADDRESS,
                                                        otp)),
                                        Collections.emptyMap(),
                                        Collections.emptyMap()));

        assertThat(ex.getMessage(), is("Invalid Principal in request"));
    }

    private String setupUserAndRetrieveInternalCommonSubId() {
        userStore.signUp(EXISTING_EMAIL_ADDRESS, "password-1", SUBJECT);
        byte[] salt = userStore.addSalt(EXISTING_EMAIL_ADDRESS);
        var internalCommonSubjectId =
                ClientSubjectHelper.calculatePairwiseIdentifier(
                        SUBJECT.getValue(), INTERNAl_SECTOR_HOST, salt);
        accountModifiersStore.setAccountRecoveryBlock(internalCommonSubjectId);
        return internalCommonSubjectId;
    }
}
