package uk.gov.di.authentication.api;

import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCScopeValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.di.authentication.frontendapi.entity.VerifyMfaCodeRequest;
import uk.gov.di.authentication.frontendapi.lambda.VerifyMfaCodeHandler;
import uk.gov.di.authentication.shared.entity.ErrorResponse;
import uk.gov.di.authentication.shared.entity.MFAMethodType;
import uk.gov.di.authentication.shared.entity.ServiceType;
import uk.gov.di.authentication.shared.helpers.NowHelper;
import uk.gov.di.authentication.shared.serialization.Json;
import uk.gov.di.authentication.sharedtest.basetest.ApiGatewayHandlerIntegrationTest;
import uk.gov.di.authentication.sharedtest.helper.AuthAppStub;

import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.di.authentication.frontendapi.domain.FrontendAuditableEvent.CODE_MAX_RETRIES_REACHED;
import static uk.gov.di.authentication.frontendapi.domain.FrontendAuditableEvent.CODE_VERIFIED;
import static uk.gov.di.authentication.frontendapi.domain.FrontendAuditableEvent.INVALID_CODE_SENT;
import static uk.gov.di.authentication.sharedtest.helper.AuditAssertionsHelper.assertTxmaAuditEventsReceived;
import static uk.gov.di.authentication.sharedtest.matchers.APIGatewayProxyResponseEventMatcher.hasJsonBody;
import static uk.gov.di.authentication.sharedtest.matchers.APIGatewayProxyResponseEventMatcher.hasStatus;

class VerifyMfaCodeIntegrationTest extends ApiGatewayHandlerIntegrationTest {
    private static final String EMAIL_ADDRESS = "test@test.com";
    private static final String USER_PASSWORD = "TestPassword123!";
    private static final String CLIENT_ID = "test-client-id";
    private static final String REDIRECT_URI = "http://localhost/redirect";
    public static final String CLIENT_SESSION_ID = "a-client-session-id";
    private static final String AUTH_APP_SECRET_BASE_32 = "ORSXG5BNORSXQ5A=";
    private static final AuthAppStub AUTH_APP_STUB = new AuthAppStub();
    private static final String CLIENT_NAME = "test-client-name";
    private String sessionId;

    @BeforeEach
    void beforeEachSetup() throws Json.JsonException {
        handler = new VerifyMfaCodeHandler(TXMA_ENABLED_CONFIGURATION_SERVICE);

        txmaAuditQueue.clear();

        this.sessionId = redis.createSession();
        setUpTest(sessionId, withScope());
    }

    private static Stream<Boolean> isRegistrationRequest() {
        return Stream.of(true, false);
    }

    @ParameterizedTest
    @MethodSource("isRegistrationRequest")
    void whenValidAuthAppCodeReturn204(boolean isRegistrationRequest) {
        setUpAuthAppRequest(isRegistrationRequest);
        String code = AUTH_APP_STUB.getAuthAppOneTimeCode(AUTH_APP_SECRET_BASE_32);
        VerifyMfaCodeRequest codeRequest =
                new VerifyMfaCodeRequest(MFAMethodType.AUTH_APP, code, isRegistrationRequest);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());
        assertThat(response, hasStatus(204));

        assertTxmaAuditEventsReceived(txmaAuditQueue, singletonList(CODE_VERIFIED));
        assertThat(accountRecoveryStore.isBlockPresent(EMAIL_ADDRESS), equalTo(false));
        assertThat(userStore.isAccountVerified(EMAIL_ADDRESS), equalTo(true));
        assertThat(userStore.isAuthAppVerified(EMAIL_ADDRESS), equalTo(true));
    }

    @ParameterizedTest
    @MethodSource("isRegistrationRequest")
    void whenValidAuthAppCodeReturn204AndClearAccountRecoveryBlockWhenPresent(
            boolean isRegistrationRequest) {
        accountRecoveryStore.addBlockWithTTL(EMAIL_ADDRESS);
        setUpAuthAppRequest(isRegistrationRequest);
        var code = AUTH_APP_STUB.getAuthAppOneTimeCode(AUTH_APP_SECRET_BASE_32);
        var codeRequest =
                new VerifyMfaCodeRequest(MFAMethodType.AUTH_APP, code, isRegistrationRequest);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());
        assertThat(response, hasStatus(204));

        assertTxmaAuditEventsReceived(txmaAuditQueue, singletonList(CODE_VERIFIED));
        assertThat(accountRecoveryStore.isBlockPresent(EMAIL_ADDRESS), equalTo(false));
        assertThat(userStore.isAccountVerified(EMAIL_ADDRESS), equalTo(true));
        assertThat(userStore.isAuthAppVerified(EMAIL_ADDRESS), equalTo(true));
    }

    @ParameterizedTest
    @MethodSource("isRegistrationRequest")
    void whenTwoMinuteOldValidAuthAppCodeReturn204(boolean isRegistrationRequest) {
        setUpAuthAppRequest(isRegistrationRequest);
        long oneMinuteAgo = NowHelper.nowMinus(2, ChronoUnit.MINUTES).getTime();
        var code = AUTH_APP_STUB.getAuthAppOneTimeCode(AUTH_APP_SECRET_BASE_32, oneMinuteAgo);
        var codeRequest =
                new VerifyMfaCodeRequest(MFAMethodType.AUTH_APP, code, isRegistrationRequest);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(204));
        assertTxmaAuditEventsReceived(txmaAuditQueue, singletonList(CODE_VERIFIED));
        assertThat(userStore.isAccountVerified(EMAIL_ADDRESS), equalTo(true));
        assertThat(userStore.isAuthAppVerified(EMAIL_ADDRESS), equalTo(true));
    }

    @ParameterizedTest
    @MethodSource("isRegistrationRequest")
    void whenFiveMinuteOldAuthAppCodeReturn400(boolean isRegistrationRequest) {
        setUpAuthAppRequest(isRegistrationRequest);
        long tenMinutesAgo = NowHelper.nowMinus(5, ChronoUnit.MINUTES).getTime();
        String code = AUTH_APP_STUB.getAuthAppOneTimeCode(AUTH_APP_SECRET_BASE_32, tenMinutesAgo);
        VerifyMfaCodeRequest codeRequest =
                new VerifyMfaCodeRequest(MFAMethodType.AUTH_APP, code, isRegistrationRequest);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(400));
        assertTxmaAuditEventsReceived(txmaAuditQueue, singletonList(INVALID_CODE_SENT));
        assertThat(userStore.isAccountVerified(EMAIL_ADDRESS), equalTo(!isRegistrationRequest));
        assertThat(userStore.isAuthAppVerified(EMAIL_ADDRESS), equalTo(!isRegistrationRequest));
    }

    @ParameterizedTest
    @MethodSource("isRegistrationRequest")
    void whenWrongSecretUsedByAuthAppReturn400(boolean isRegistrationRequest) {
        setUpAuthAppRequest(isRegistrationRequest);
        String invalidCode = AUTH_APP_STUB.getAuthAppOneTimeCode("O5ZG63THFVZWKY3SMV2A====");
        VerifyMfaCodeRequest codeRequest =
                new VerifyMfaCodeRequest(
                        MFAMethodType.AUTH_APP, invalidCode, isRegistrationRequest);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(400));
        assertThat(response, hasJsonBody(ErrorResponse.ERROR_1043));
        assertTxmaAuditEventsReceived(txmaAuditQueue, singletonList(INVALID_CODE_SENT));
        assertThat(userStore.isAccountVerified(EMAIL_ADDRESS), equalTo(!isRegistrationRequest));
        assertThat(userStore.isAuthAppVerified(EMAIL_ADDRESS), equalTo(!isRegistrationRequest));
    }

    @ParameterizedTest
    @MethodSource("isRegistrationRequest")
    void whenWrongSecretUsedByAuthAppReturn400AndNotClearAccountRecoveryBlockWhenPresent(
            boolean isRegistrationRequest) {
        accountRecoveryStore.addBlockWithTTL(EMAIL_ADDRESS);
        setUpAuthAppRequest(isRegistrationRequest);
        String invalidCode = AUTH_APP_STUB.getAuthAppOneTimeCode("O5ZG63THFVZWKY3SMV2A====");
        VerifyMfaCodeRequest codeRequest =
                new VerifyMfaCodeRequest(MFAMethodType.AUTH_APP, invalidCode, true);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(400));
        assertThat(response, hasJsonBody(ErrorResponse.ERROR_1043));
        assertTxmaAuditEventsReceived(txmaAuditQueue, singletonList(INVALID_CODE_SENT));
        assertThat(accountRecoveryStore.isBlockPresent(EMAIL_ADDRESS), equalTo(true));
        assertThat(userStore.isAccountVerified(EMAIL_ADDRESS), equalTo(!isRegistrationRequest));
        assertThat(userStore.isAuthAppVerified(EMAIL_ADDRESS), equalTo(!isRegistrationRequest));
    }

    @Test
    void whenAuthAppMfaMethodIsNotEnabledReturn400() {
        userStore.addMfaMethod(
                EMAIL_ADDRESS, MFAMethodType.AUTH_APP, true, false, AUTH_APP_SECRET_BASE_32);
        String code = AUTH_APP_STUB.getAuthAppOneTimeCode(AUTH_APP_SECRET_BASE_32);
        VerifyMfaCodeRequest codeRequest =
                new VerifyMfaCodeRequest(MFAMethodType.AUTH_APP, code, true);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(400));
        assertThat(response, hasJsonBody(ErrorResponse.ERROR_1043));
        assertTxmaAuditEventsReceived(txmaAuditQueue, singletonList(INVALID_CODE_SENT));
    }

    @Test
    void whenParametersMissingReturn400() {
        userStore.addMfaMethod(
                EMAIL_ADDRESS, MFAMethodType.AUTH_APP, true, true, AUTH_APP_SECRET_BASE_32);
        String code = AUTH_APP_STUB.getAuthAppOneTimeCode(AUTH_APP_SECRET_BASE_32);
        VerifyMfaCodeRequest codeRequest = new VerifyMfaCodeRequest(null, code, true);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(400));
        assertThat(response, hasJsonBody(ErrorResponse.ERROR_1001));
    }

    @ParameterizedTest
    @MethodSource("isRegistrationRequest")
    void whenAuthAppCodeSubmissionBlockedReturn400(boolean isRegistrationRequest) {
        setUpAuthAppRequest(isRegistrationRequest);
        String code = AUTH_APP_STUB.getAuthAppOneTimeCode(AUTH_APP_SECRET_BASE_32);
        VerifyMfaCodeRequest codeRequest =
                new VerifyMfaCodeRequest(MFAMethodType.AUTH_APP, code, isRegistrationRequest);

        redis.blockMfaCodesForEmail(EMAIL_ADDRESS);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(400));
        assertThat(response, hasJsonBody(ErrorResponse.ERROR_1042));
        assertTxmaAuditEventsReceived(txmaAuditQueue, singletonList(CODE_MAX_RETRIES_REACHED));
        assertThat(userStore.isAccountVerified(EMAIL_ADDRESS), equalTo(!isRegistrationRequest));
        assertThat(userStore.isAuthAppVerified(EMAIL_ADDRESS), equalTo(!isRegistrationRequest));
    }

    @Test
    void whenAuthAppCodeRetriesLimitExceededForSignInBlockEmailAndReturn400()
            throws Json.JsonException {
        setUpAuthAppRequest(false);
        String invalidCode = AUTH_APP_STUB.getAuthAppOneTimeCode("O5ZG63THFVZWKY3SMV2A====");
        VerifyMfaCodeRequest codeRequest =
                new VerifyMfaCodeRequest(MFAMethodType.AUTH_APP, invalidCode, false);

        for (int i = 0; i < 5; i++) {
            makeRequest(
                    Optional.of(codeRequest),
                    constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                    Map.of());
        }

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(400));
        assertThat(response, hasJsonBody(ErrorResponse.ERROR_1042));
        assertEquals(0, redis.getSession(sessionId).getRetryCount());
        assertThat(userStore.isAccountVerified(EMAIL_ADDRESS), equalTo(true));
        assertThat(userStore.isAuthAppVerified(EMAIL_ADDRESS), equalTo(true));
    }

    @Test
    void whenValidPhoneNumberCodeForRegistrationReturn204() {
        var code = redis.generateAndSavePhoneNumberCode(EMAIL_ADDRESS, 900);
        var codeRequest = new VerifyMfaCodeRequest(MFAMethodType.SMS, code, true);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(204));
        assertTxmaAuditEventsReceived(txmaAuditQueue, List.of(CODE_VERIFIED));
    }

    @Test
    void whenInvalidPhoneNumberCodeHasExpiredForRegistrationReturn400() {
        var code = redis.generateAndSavePhoneNumberCode(EMAIL_ADDRESS, 1);
        var codeRequest = new VerifyMfaCodeRequest(MFAMethodType.SMS, code, true);

        await().pollDelay(Duration.ofSeconds(2)).untilAsserted(() -> assertTrue(true));

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(400));
        assertThat(response, hasJsonBody(ErrorResponse.ERROR_1037));
        assertTxmaAuditEventsReceived(txmaAuditQueue, List.of(INVALID_CODE_SENT));
    }

    @Test
    void whenInvalidPhoneNumberCodeForRegistrationReturn400() {
        var codeRequest = new VerifyMfaCodeRequest(MFAMethodType.SMS, "123456", true);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(400));
        assertThat(response, hasJsonBody(ErrorResponse.ERROR_1037));
        assertTxmaAuditEventsReceived(txmaAuditQueue, List.of(INVALID_CODE_SENT));
    }

    @Test
    void whenPhoneNumberCodeIsBlockedForRegistrationReturn400() throws Json.JsonException {
        redis.addEmailToSession(sessionId, EMAIL_ADDRESS);
        redis.blockMfaCodesForEmail(EMAIL_ADDRESS);

        var codeRequest = new VerifyMfaCodeRequest(MFAMethodType.SMS, "123456", true);

        var response =
                makeRequest(
                        Optional.of(codeRequest), constructFrontendHeaders(sessionId), Map.of());

        assertThat(response, hasStatus(400));
        assertThat(response, hasJsonBody(ErrorResponse.ERROR_1034));
        assertTxmaAuditEventsReceived(txmaAuditQueue, singletonList(CODE_MAX_RETRIES_REACHED));
    }

    @Test
    void whenPhoneNumberCodeRetriesLimitExceededForRegistrationBlockEmailAndReturn400()
            throws Json.JsonException {
        redis.addEmailToSession(sessionId, EMAIL_ADDRESS);

        var codeRequest = new VerifyMfaCodeRequest(MFAMethodType.SMS, "123456", true);

        for (int i = 0; i < 5; i++) {
            makeRequest(
                    Optional.of(codeRequest),
                    constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                    Map.of());
        }

        var response =
                makeRequest(
                        Optional.of(codeRequest), constructFrontendHeaders(sessionId), Map.of());

        assertThat(response, hasStatus(400));
        assertThat(response, hasJsonBody(ErrorResponse.ERROR_1034));
        assertTxmaAuditEventsReceived(
                txmaAuditQueue,
                List.of(
                        INVALID_CODE_SENT,
                        INVALID_CODE_SENT,
                        INVALID_CODE_SENT,
                        INVALID_CODE_SENT,
                        INVALID_CODE_SENT,
                        CODE_MAX_RETRIES_REACHED));
    }

    private void setUpTest(String sessionId, Scope scope) throws Json.JsonException {
        userStore.signUp(EMAIL_ADDRESS, USER_PASSWORD);
        redis.addEmailToSession(sessionId, EMAIL_ADDRESS);
        AuthenticationRequest authRequest =
                new AuthenticationRequest.Builder(
                                ResponseType.CODE,
                                scope,
                                new ClientID(CLIENT_ID),
                                URI.create(REDIRECT_URI))
                        .nonce(new Nonce())
                        .state(new State())
                        .build();
        redis.createClientSession(CLIENT_SESSION_ID, CLIENT_NAME, authRequest.toParameters());
        clientStore.registerClient(
                CLIENT_ID,
                "test-client",
                singletonList("redirect-url"),
                singletonList(EMAIL_ADDRESS),
                List.of("openid", "email", "phone"),
                "public-key",
                singletonList("http://localhost/post-redirect-logout"),
                "https://example.com",
                String.valueOf(ServiceType.MANDATORY),
                "https://test.com",
                "public",
                true);
    }

    private Scope withScope() {
        Scope scope = new Scope();
        scope.add(OIDCScopeValue.OPENID);
        scope.add(OIDCScopeValue.EMAIL);
        return scope;
    }

    public void setUpAuthAppRequest(boolean isRegistrationRequest) {
        boolean mfaVerified = !isRegistrationRequest;
        if (mfaVerified) {
            userStore.setAccountVerified(EMAIL_ADDRESS);
        }
        userStore.addMfaMethod(
                EMAIL_ADDRESS, MFAMethodType.AUTH_APP, mfaVerified, true, AUTH_APP_SECRET_BASE_32);
    }
}
