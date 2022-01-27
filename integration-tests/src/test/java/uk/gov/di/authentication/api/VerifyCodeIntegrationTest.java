package uk.gov.di.authentication.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCScopeValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.di.authentication.frontendapi.entity.VerifyCodeRequest;
import uk.gov.di.authentication.frontendapi.lambda.VerifyCodeHandler;
import uk.gov.di.authentication.shared.entity.BaseAPIResponse;
import uk.gov.di.authentication.shared.entity.ClientConsent;
import uk.gov.di.authentication.shared.entity.ErrorResponse;
import uk.gov.di.authentication.shared.entity.NotificationType;
import uk.gov.di.authentication.shared.entity.ServiceType;
import uk.gov.di.authentication.shared.entity.SessionState;
import uk.gov.di.authentication.shared.entity.ValidScopes;
import uk.gov.di.authentication.sharedtest.basetest.ApiGatewayHandlerIntegrationTest;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.di.authentication.frontendapi.domain.FrontendAuditableEvent.CODE_VERIFIED;
import static uk.gov.di.authentication.shared.entity.NotificationType.VERIFY_EMAIL;
import static uk.gov.di.authentication.sharedtest.helper.AuditAssertionsHelper.assertEventTypesReceived;
import static uk.gov.di.authentication.sharedtest.helper.AuditAssertionsHelper.assertNoAuditEventsReceived;
import static uk.gov.di.authentication.sharedtest.matchers.APIGatewayProxyResponseEventMatcher.hasStatus;

public class VerifyCodeIntegrationTest extends ApiGatewayHandlerIntegrationTest {

    private static final String EMAIL_ADDRESS = "test@test.com";
    private static final String CLIENT_ID = "test-client-id";
    private static final String REDIRECT_URI = "http://localhost/redirect";
    public static final String CLIENT_SESSION_ID = "a-client-session-id";

    @BeforeEach
    void setup() {
        handler = new VerifyCodeHandler(TEST_CONFIGURATION_SERVICE);
    }

    @Test
    public void shouldCallVerifyCodeEndpointToVerifyEmailCodeAndReturn200() throws IOException {
        String sessionId = redis.createSession();
        setUpTestWithoutSignUp(sessionId, withScope(), SessionState.VERIFY_EMAIL_CODE_SENT);
        String code = redis.generateAndSaveEmailCode(EMAIL_ADDRESS, 900);
        VerifyCodeRequest codeRequest = new VerifyCodeRequest(VERIFY_EMAIL, code);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());
        assertThat(response, hasStatus(200));

        assertEventTypesReceived(auditTopic, List.of(CODE_VERIFIED));
    }

    @Test
    public void shouldCallVerifyCodeEndpointAndReturn400WitUpdatedStateWhenEmailCodeHasExpired()
            throws IOException, InterruptedException {
        String sessionId = redis.createSession();
        setUpTestWithoutSignUp(sessionId, withScope(), SessionState.VERIFY_EMAIL_CODE_SENT);

        String code = redis.generateAndSaveEmailCode(EMAIL_ADDRESS, 2);
        VerifyCodeRequest codeRequest = new VerifyCodeRequest(VERIFY_EMAIL, code);

        TimeUnit.SECONDS.sleep(3);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(400));

        BaseAPIResponse codeResponse =
                objectMapper.readValue(response.getBody(), BaseAPIResponse.class);
        assertEquals(SessionState.EMAIL_CODE_NOT_VALID, codeResponse.getSessionState());

        assertNoAuditEventsReceived(auditTopic);
    }

    @Test
    public void shouldReturn400WithNewStateWhenUserTriesEmailCodeThatTheyHaveAlreadyUsed()
            throws IOException {
        String sessionId = redis.createSession();
        setUpTestWithoutSignUp(sessionId, withScope(), SessionState.VERIFY_EMAIL_CODE_SENT);
        String code = redis.generateAndSaveEmailCode(EMAIL_ADDRESS, 900);
        VerifyCodeRequest codeRequest = new VerifyCodeRequest(VERIFY_EMAIL, code);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(200));
        BaseAPIResponse codeResponse1 =
                objectMapper.readValue(response.getBody(), BaseAPIResponse.class);
        assertEquals(SessionState.EMAIL_CODE_VERIFIED, codeResponse1.getSessionState());

        var response2 =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response2, hasStatus(400));

        BaseAPIResponse codeResponse =
                objectMapper.readValue(response2.getBody(), BaseAPIResponse.class);
        assertEquals(SessionState.EMAIL_CODE_NOT_VALID, codeResponse.getSessionState());

        assertEventTypesReceived(auditTopic, List.of(CODE_VERIFIED));
    }

    @Test
    public void shouldCallVerifyCodeEndpointToVerifyPhoneCodeAndReturn200() throws IOException {
        String sessionId = redis.createSession();
        Scope scope = withScope();
        setUpTestWithoutClientConsent(sessionId, scope, SessionState.VERIFY_PHONE_NUMBER_CODE_SENT);
        Set<String> claims = ValidScopes.getClaimsForListOfScopes(scope.toStringList());
        ClientConsent clientConsent =
                new ClientConsent(
                        CLIENT_ID, claims, LocalDateTime.now(ZoneId.of("UTC")).toString());
        userStore.updateConsent(EMAIL_ADDRESS, clientConsent);
        String code = redis.generateAndSavePhoneNumberCode(EMAIL_ADDRESS, 900);
        VerifyCodeRequest codeRequest =
                new VerifyCodeRequest(NotificationType.VERIFY_PHONE_NUMBER, code);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(200));

        BaseAPIResponse codeResponse =
                objectMapper.readValue(response.getBody(), BaseAPIResponse.class);
        assertEquals(SessionState.PHONE_NUMBER_CODE_VERIFIED, codeResponse.getSessionState());

        assertEventTypesReceived(auditTopic, List.of(CODE_VERIFIED));
    }

    @Test
    public void shouldCallVerifyCodeEndpointToVerifyPhoneCodeAndReturnConsentRequiredState()
            throws IOException {
        String sessionId = redis.createSession();
        setUpTestWithoutClientConsent(
                sessionId, withScope(), SessionState.VERIFY_PHONE_NUMBER_CODE_SENT);
        String code = redis.generateAndSavePhoneNumberCode(EMAIL_ADDRESS, 900);
        VerifyCodeRequest codeRequest =
                new VerifyCodeRequest(NotificationType.VERIFY_PHONE_NUMBER, code);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(200));

        BaseAPIResponse codeResponse =
                objectMapper.readValue(response.getBody(), BaseAPIResponse.class);
        assertEquals(SessionState.CONSENT_REQUIRED, codeResponse.getSessionState());

        assertEventTypesReceived(auditTopic, List.of(CODE_VERIFIED));
    }

    @Test
    public void
            shouldCallVerifyCodeEndpointAndReturn400WitUpdatedStateWhenPhoneNumberCodeHasExpired()
                    throws IOException, InterruptedException {
        String sessionId = redis.createSession();
        setUpTestWithoutSignUp(sessionId, withScope(), SessionState.VERIFY_PHONE_NUMBER_CODE_SENT);

        String code = redis.generateAndSavePhoneNumberCode(EMAIL_ADDRESS, 2);
        VerifyCodeRequest codeRequest =
                new VerifyCodeRequest(NotificationType.VERIFY_PHONE_NUMBER, code);

        TimeUnit.SECONDS.sleep(3);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(400));

        BaseAPIResponse codeResponse =
                objectMapper.readValue(response.getBody(), BaseAPIResponse.class);
        assertEquals(SessionState.PHONE_NUMBER_CODE_NOT_VALID, codeResponse.getSessionState());

        assertNoAuditEventsReceived(auditTopic);
    }

    @Test
    public void shouldReturnMaxCodesReachedIfPhoneNumberCodeIsBlocked() throws IOException {
        String sessionId = redis.createSession();
        redis.addEmailToSession(sessionId, EMAIL_ADDRESS);
        redis.setSessionState(sessionId, SessionState.PHONE_NUMBER_CODE_NOT_VALID);
        redis.blockPhoneCode(EMAIL_ADDRESS);

        VerifyCodeRequest codeRequest =
                new VerifyCodeRequest(NotificationType.VERIFY_PHONE_NUMBER, "123456");

        var response =
                makeRequest(
                        Optional.of(codeRequest), constructFrontendHeaders(sessionId), Map.of());

        assertThat(response, hasStatus(400));

        BaseAPIResponse codeResponse =
                objectMapper.readValue(response.getBody(), BaseAPIResponse.class);
        assertEquals(
                SessionState.PHONE_NUMBER_CODE_MAX_RETRIES_REACHED, codeResponse.getSessionState());

        assertNoAuditEventsReceived(auditTopic);
    }

    @Test
    public void shouldReturnMaxCodesReachedIfEmailCodeIsBlocked() throws IOException {
        String sessionId = redis.createSession();
        redis.setSessionState(sessionId, SessionState.EMAIL_CODE_NOT_VALID);
        redis.addEmailToSession(sessionId, EMAIL_ADDRESS);
        redis.blockPhoneCode(EMAIL_ADDRESS);

        VerifyCodeRequest codeRequest = new VerifyCodeRequest(VERIFY_EMAIL, "123456");

        var response =
                makeRequest(
                        Optional.of(codeRequest), constructFrontendHeaders(sessionId), Map.of());

        assertThat(response, hasStatus(400));

        BaseAPIResponse codeResponse =
                objectMapper.readValue(response.getBody(), BaseAPIResponse.class);
        assertEquals(SessionState.EMAIL_CODE_MAX_RETRIES_REACHED, codeResponse.getSessionState());

        assertNoAuditEventsReceived(auditTopic);
    }

    @Test
    public void shouldReturn400IfStateTransitionIsInvalid() throws IOException {
        String sessionId = redis.createSession();
        setUpTestWithoutSignUp(sessionId, withScope(), SessionState.NEW);

        String code = redis.generateAndSaveEmailCode(EMAIL_ADDRESS, 900);
        VerifyCodeRequest codeRequest = new VerifyCodeRequest(VERIFY_EMAIL, code);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(400));

        assertEquals(
                new ObjectMapper().writeValueAsString(ErrorResponse.ERROR_1017),
                response.getBody());

        assertNoAuditEventsReceived(auditTopic);
    }

    @Test
    public void shouldReturn400IfStateTransitionIsInvalid_PhoneNumber() throws IOException {
        String sessionId = redis.createSession();
        setUpTestWithoutSignUp(sessionId, withScope(), SessionState.NEW);

        String code = redis.generateAndSavePhoneNumberCode(EMAIL_ADDRESS, 900);
        VerifyCodeRequest codeRequest =
                new VerifyCodeRequest(NotificationType.VERIFY_PHONE_NUMBER, code);
        userStore.signUp(EMAIL_ADDRESS, "password");

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(400));

        assertEquals(
                new ObjectMapper().writeValueAsString(ErrorResponse.ERROR_1017),
                response.getBody());

        assertNoAuditEventsReceived(auditTopic);
    }

    @Test
    public void shouldReturnStateOfMfaCodeVerifiedWhenUserHasAcceptedCurrentTermsAndConditions()
            throws Exception {
        String sessionId = redis.createSession();
        Scope scope = new Scope();
        scope.add(OIDCScopeValue.OPENID);
        scope.add(OIDCScopeValue.EMAIL);
        scope.add(OIDCScopeValue.PHONE);
        setUpTestWithoutClientConsent(sessionId, withScope(), SessionState.MFA_SMS_CODE_SENT);
        userStore.updateTermsAndConditions(EMAIL_ADDRESS, "1.0");
        ClientConsent clientConsent =
                new ClientConsent(
                        CLIENT_ID,
                        ValidScopes.getClaimsForListOfScopes(scope.toStringList()),
                        LocalDateTime.now().toString());
        userStore.updateConsent(EMAIL_ADDRESS, clientConsent);

        String code = redis.generateAndSaveMfaCode(EMAIL_ADDRESS, 900);
        VerifyCodeRequest codeRequest = new VerifyCodeRequest(NotificationType.MFA_SMS, code);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(200));

        BaseAPIResponse codeResponse =
                objectMapper.readValue(response.getBody(), BaseAPIResponse.class);
        assertEquals(SessionState.MFA_CODE_VERIFIED, codeResponse.getSessionState());

        assertEventTypesReceived(auditTopic, List.of(CODE_VERIFIED));
    }

    @Test
    public void shouldReturnStateOfUpdatedTermsAndConditionsWhenUserHasNotAcceptedCurrentVersion()
            throws IOException {
        String sessionId = redis.createSession();
        Scope scope = new Scope();
        scope.add(OIDCScopeValue.OPENID);
        scope.add(OIDCScopeValue.EMAIL);
        scope.add(OIDCScopeValue.PHONE);
        setUpTestWithoutClientConsent(sessionId, scope, SessionState.MFA_SMS_CODE_SENT);

        userStore.updateTermsAndConditions(EMAIL_ADDRESS, "0.1");

        String code = redis.generateAndSaveMfaCode(EMAIL_ADDRESS, 900);
        VerifyCodeRequest codeRequest = new VerifyCodeRequest(NotificationType.MFA_SMS, code);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(200));

        BaseAPIResponse codeResponse =
                objectMapper.readValue(response.getBody(), BaseAPIResponse.class);
        assertEquals(SessionState.UPDATED_TERMS_AND_CONDITIONS, codeResponse.getSessionState());

        assertEventTypesReceived(auditTopic, List.of(CODE_VERIFIED));
    }

    @Test
    public void shouldReturn400IfStateTransitionIsInvalid_SMS() throws IOException {
        String sessionId = redis.createSession();
        setUpTestWithoutSignUp(sessionId, withScope(), SessionState.NEW);

        String code = redis.generateAndSaveEmailCode(EMAIL_ADDRESS, 900);
        VerifyCodeRequest codeRequest = new VerifyCodeRequest(NotificationType.MFA_SMS, code);

        var response =
                makeRequest(
                        Optional.of(codeRequest),
                        constructFrontendHeaders(sessionId, CLIENT_SESSION_ID),
                        Map.of());

        assertThat(response, hasStatus(400));
        assertEquals(
                new ObjectMapper().writeValueAsString(ErrorResponse.ERROR_1017),
                response.getBody());

        assertNoAuditEventsReceived(auditTopic);
    }

    private void setUpTestWithoutSignUp(String sessionId, Scope scope, SessionState sessionState)
            throws JsonProcessingException {
        redis.addEmailToSession(sessionId, EMAIL_ADDRESS);
        redis.setSessionState(sessionId, sessionState);
        AuthenticationRequest authRequest =
                new AuthenticationRequest.Builder(
                                ResponseType.CODE,
                                scope,
                                new ClientID(CLIENT_ID),
                                URI.create(REDIRECT_URI))
                        .nonce(new Nonce())
                        .state(new State())
                        .build();
        redis.createClientSession(CLIENT_SESSION_ID, authRequest.toParameters());
        clientStore.registerClient(
                CLIENT_ID,
                "test-client",
                singletonList("redirect-url"),
                singletonList(EMAIL_ADDRESS),
                List.of("openid", "email", "phone"),
                "public-key",
                singletonList("http://localhost/post-redirect-logout"),
                String.valueOf(ServiceType.MANDATORY),
                "https://test.com",
                "public",
                true);
    }

    private void setUpTestWithoutClientConsent(
            String sessionId, Scope scope, SessionState sessionState)
            throws JsonProcessingException {
        setUpTestWithoutSignUp(sessionId, scope, sessionState);
        userStore.signUp(EMAIL_ADDRESS, "password");
    }

    private Scope withScope() {
        Scope scope = new Scope();
        scope.add(OIDCScopeValue.OPENID);
        scope.add(OIDCScopeValue.EMAIL);
        return scope;
    }
}
