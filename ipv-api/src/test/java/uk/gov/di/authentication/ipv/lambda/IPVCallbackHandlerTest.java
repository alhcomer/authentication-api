package uk.gov.di.authentication.ipv.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.nimbusds.oauth2.sdk.AccessTokenResponse;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.OAuth2Error;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.id.Subject;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.token.Tokens;
import com.nimbusds.openid.connect.sdk.AuthenticationErrorResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCScopeValue;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.http.client.utils.URIBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;
import uk.gov.di.authentication.ipv.domain.IPVAuditableEvent;
import uk.gov.di.authentication.ipv.entity.LogIds;
import uk.gov.di.authentication.ipv.entity.SPOTClaims;
import uk.gov.di.authentication.ipv.entity.SPOTRequest;
import uk.gov.di.authentication.ipv.services.IPVAuthorisationService;
import uk.gov.di.authentication.ipv.services.IPVTokenService;
import uk.gov.di.authentication.shared.entity.ClientRegistry;
import uk.gov.di.authentication.shared.entity.ClientSession;
import uk.gov.di.authentication.shared.entity.IdentityClaims;
import uk.gov.di.authentication.shared.entity.LevelOfConfidence;
import uk.gov.di.authentication.shared.entity.ResponseHeaders;
import uk.gov.di.authentication.shared.entity.Session;
import uk.gov.di.authentication.shared.entity.UserProfile;
import uk.gov.di.authentication.shared.entity.ValidClaims;
import uk.gov.di.authentication.shared.helpers.ClientSubjectHelper;
import uk.gov.di.authentication.shared.serialization.Json;
import uk.gov.di.authentication.shared.services.AuditService;
import uk.gov.di.authentication.shared.services.AwsSqsClient;
import uk.gov.di.authentication.shared.services.ClientSessionService;
import uk.gov.di.authentication.shared.services.ConfigurationService;
import uk.gov.di.authentication.shared.services.DynamoClientService;
import uk.gov.di.authentication.shared.services.DynamoIdentityService;
import uk.gov.di.authentication.shared.services.DynamoService;
import uk.gov.di.authentication.shared.services.SerializationService;
import uk.gov.di.authentication.shared.services.SessionService;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.di.authentication.sharedtest.matchers.APIGatewayProxyResponseEventMatcher.hasStatus;

class IPVCallbackHandlerTest {

    private static final Subject SUBJECT = new Subject();
    private final Context context = mock(Context.class);
    private final ConfigurationService configService = mock(ConfigurationService.class);
    private final IPVAuthorisationService responseService = mock(IPVAuthorisationService.class);
    private final IPVTokenService ipvTokenService = mock(IPVTokenService.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final DynamoService dynamoService = mock(DynamoService.class);
    private final ClientSessionService clientSessionService = mock(ClientSessionService.class);
    private final DynamoClientService dynamoClientService = mock(DynamoClientService.class);
    private final DynamoIdentityService dynamoIdentityService = mock(DynamoIdentityService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AwsSqsClient awsSqsClient = mock(AwsSqsClient.class);
    private static final URI LOGIN_URL = URI.create("https://example.com");
    private static final String OIDC_BASE_URL = "https://base-url.com";
    private static final AuthorizationCode AUTH_CODE = new AuthorizationCode();
    private static final String COOKIE = "Cookie";
    private static final String SESSION_ID = "a-session-id";
    private static final String CLIENT_SESSION_ID = "a-client-session-id";
    private static final String REQUEST_ID = "a-request-id";
    private static final String PERSISTENT_SESSION_ID = "a-persistent-id";
    private static final String TEST_EMAIL_ADDRESS = "test@test.com";
    private static final URI REDIRECT_URI = URI.create("test-uri");
    private static final State RP_STATE = new State();
    private static final URI IPV_URI = URI.create("http://ipv/");
    private static final ClientID CLIENT_ID = new ClientID();
    private static final String ADDRESS_CLAIM =
            "[{\"buildingNumber\":\"10\",\"streetName\":\"DowningStreet\",\"dependentAddressLocality\":\"Westminster\",\"addressLocality\":\"London\",\"postalCode\":\"SW1A2AA\",\"addressCountry\":\"GB\",\"validFrom\":\"2019-07-24\"}]";
    private static final String PASSPORT_CLAIM =
            "[{\"documentNumber\":\"12345678\",\"expiryDate\":\"2022-02-01\"}]";
    private static final String CORE_IDENTITY_CLAIM =
            "{\"name\":[{\"nameParts\":[{\"type\":\"GivenName\",\"value\":\"kenneth\"},{\"type\":\"FamilyName\",\"value\":\"decerqueira\"}]}],\"birthDate\":[{\"value\":\"1964-11-07\"}]}";

    private static final String CREDENTIAL_JWT_CLAIM = "some-credential-jwt";

    private static final Subject PUBLIC_SUBJECT =
            new Subject("TsEVC7vg0NPAmzB33vRUFztL2c0-fecKWKcc73fuDhc");
    private static final State STATE = new State();
    private IPVCallbackHandler handler;
    private final byte[] salt = "Mmc48imEuO5kkVW7NtXVtx5h0mbCTfXsqXdWvbRMzdw=".getBytes();
    private final ClientRegistry clientRegistry = generateClientRegistry();
    private final UserProfile userProfile = generateUserProfile();

    private final Session session = new Session(SESSION_ID).setEmailAddress(TEST_EMAIL_ADDRESS);

    private final ClientSession clientSession =
            new ClientSession(generateAuthRequest().toParameters(), null, null);

    private final Json objectMapper = SerializationService.getInstance();

    @BeforeEach
    void setUp() {
        handler =
                new IPVCallbackHandler(
                        configService,
                        responseService,
                        ipvTokenService,
                        sessionService,
                        dynamoService,
                        clientSessionService,
                        dynamoClientService,
                        auditService,
                        awsSqsClient,
                        dynamoIdentityService);
        when(configService.getLoginURI()).thenReturn(LOGIN_URL);
        when(configService.getOidcApiBaseURL()).thenReturn(Optional.of(OIDC_BASE_URL));
        when(configService.isSpotEnabled()).thenReturn(true);
        when(configService.getIPVBackendURI()).thenReturn(IPV_URI);
        when(configService.getIPVSector()).thenReturn(OIDC_BASE_URL + "/trustmark");
        when(configService.isIdentityEnabled()).thenReturn(true);
        when(context.getAwsRequestId()).thenReturn(REQUEST_ID);
    }

    @Test
    void shouldThrowWhenIdentityIsNotEnabled() {
        when(configService.isIdentityEnabled()).thenReturn(false);
        usingValidSession();
        usingValidClientSession();

        var exception =
                assertThrows(
                        RuntimeException.class,
                        () -> makeHandlerRequest(getApiGatewayProxyRequestEvent(null)),
                        "Expected to throw exception");

        assertThat(exception.getMessage(), equalTo("Identity is not enabled"));
    }

    @Test
    void shouldNotInvokeSPOTAndReturnAccessDeniedErrorToRPWhenP0() {
        usingValidSession();
        usingValidClientSession();
        var userIdentityUserInfo =
                new UserInfo(
                        new JSONObject(
                                Map.of(
                                        "sub", "sub-val",
                                        "vot", "P0",
                                        "vtm", OIDC_BASE_URL + "/trustmark")));

        var response = makeHandlerRequest(getApiGatewayProxyRequestEvent(userIdentityUserInfo));
        var expectedURI =
                new AuthenticationErrorResponse(
                                URI.create(REDIRECT_URI.toString()),
                                OAuth2Error.ACCESS_DENIED,
                                RP_STATE,
                                null)
                        .toURI()
                        .toString();

        assertThat(response, hasStatus(302));
        assertThat(response.getHeaders().get(ResponseHeaders.LOCATION), equalTo(expectedURI));

        verifyNoInteractions(dynamoIdentityService);
        verifyAuditEvent(IPVAuditableEvent.IPV_AUTHORISATION_RESPONSE_RECEIVED);
        verifyAuditEvent(IPVAuditableEvent.IPV_SUCCESSFUL_TOKEN_RESPONSE_RECEIVED);
        verifyAuditEvent(IPVAuditableEvent.IPV_SUCCESSFUL_IDENTITY_RESPONSE_RECEIVED);
        verifyNoInteractions(awsSqsClient);
    }

    private static Stream<Arguments> additionalClaims() {
        return Stream.of(
                Arguments.of(Map.of(ValidClaims.ADDRESS.getValue(), ADDRESS_CLAIM)),
                Arguments.of(Map.of(ValidClaims.PASSPORT.getValue(), PASSPORT_CLAIM)),
                Arguments.of(emptyMap()),
                Arguments.of(
                        Map.of(
                                ValidClaims.ADDRESS.getValue(),
                                ADDRESS_CLAIM,
                                ValidClaims.PASSPORT.getValue(),
                                PASSPORT_CLAIM)));
    }

    @ParameterizedTest
    @MethodSource("additionalClaims")
    void shouldInvokeSPOTAndRedirectToFrontendCallbackForSuccessfulResponseAtP2(
            Map<String, String> additionalClaims) throws URISyntaxException, Json.JsonException {
        usingValidSession();
        usingValidClientSession();

        Map<String, Object> userIdentityAdditionalClaims = new HashMap<>();

        for (var entry : additionalClaims.entrySet()) {
            userIdentityAdditionalClaims.put(
                    entry.getKey(), objectMapper.readValue(entry.getValue(), JSONArray.class));
        }

        var claims =
                new HashMap<String, Object>(
                        Map.of(
                                "sub",
                                "sub-val",
                                "vot",
                                "P2",
                                "vtm",
                                OIDC_BASE_URL + "/trustmark",
                                "https://vocab.account.gov.uk/v1/coreIdentity",
                                CORE_IDENTITY_CLAIM,
                                "https://vocab.account.gov.uk/v1/credentialJWT",
                                CREDENTIAL_JWT_CLAIM));
        claims.putAll(userIdentityAdditionalClaims);

        var response =
                makeHandlerRequest(
                        getApiGatewayProxyRequestEvent(new UserInfo(new JSONObject(claims))));

        assertThat(response, hasStatus(302));
        var expectedRedirectURI = new URIBuilder(LOGIN_URL).setPath("ipv-callback").build();
        assertThat(response.getHeaders().get("Location"), equalTo(expectedRedirectURI.toString()));
        var expectedPairwiseSub =
                ClientSubjectHelper.getSubject(userProfile, clientRegistry, dynamoService);
        verify(awsSqsClient)
                .send(
                        objectMapper.writeValueAsString(
                                new SPOTRequest(
                                        SPOTClaims.builder()
                                                .withVot(LevelOfConfidence.MEDIUM_LEVEL.getValue())
                                                .withVtm(OIDC_BASE_URL + "/trustmark")
                                                .withClaim(
                                                        IdentityClaims.CORE_IDENTITY.getValue(),
                                                        CORE_IDENTITY_CLAIM)
                                                .withClaim(
                                                        IdentityClaims.CREDENTIAL_JWT.getValue(),
                                                        CREDENTIAL_JWT_CLAIM)
                                                .build(),
                                        SUBJECT.getValue(),
                                        salt,
                                        "test.com",
                                        expectedPairwiseSub.getValue(),
                                        new LogIds(
                                                session.getSessionId(),
                                                PERSISTENT_SESSION_ID,
                                                REQUEST_ID,
                                                CLIENT_ID.getValue()))));

        verify(dynamoIdentityService)
                .addAdditionalClaims(expectedPairwiseSub.getValue(), additionalClaims);
        verifyAuditEvent(IPVAuditableEvent.IPV_AUTHORISATION_RESPONSE_RECEIVED);
        verifyAuditEvent(IPVAuditableEvent.IPV_SUCCESSFUL_TOKEN_RESPONSE_RECEIVED);
        verifyAuditEvent(IPVAuditableEvent.IPV_SUCCESSFUL_IDENTITY_RESPONSE_RECEIVED);
        verifyAuditEvent(IPVAuditableEvent.IPV_SPOT_REQUESTED);
        verifyNoMoreInteractions(auditService);
    }

    @Test
    void shouldNotInvokeSPOTAndThrowWhenVTMMismatch() {
        usingValidSession();
        usingValidClientSession();
        var userIdentityUserInfo =
                new UserInfo(
                        new JSONObject(
                                Map.of(
                                        "sub", "sub-val",
                                        "vot", "P2",
                                        "vtm", OIDC_BASE_URL + "/invalid-trustmark")));
        var runtimeException =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                makeHandlerRequest(
                                        getApiGatewayProxyRequestEvent(userIdentityUserInfo)),
                        "Expected to throw exception");

        assertThat(runtimeException.getMessage(), equalTo("IPV trustmark is invalid"));
        verifyAuditEvent(IPVAuditableEvent.IPV_AUTHORISATION_RESPONSE_RECEIVED);
        verifyAuditEvent(IPVAuditableEvent.IPV_SUCCESSFUL_TOKEN_RESPONSE_RECEIVED);
        verifyAuditEvent(IPVAuditableEvent.IPV_SUCCESSFUL_IDENTITY_RESPONSE_RECEIVED);
        verifyNoMoreInteractions(auditService);
        verifyNoInteractions(awsSqsClient);
        verifyNoInteractions(dynamoIdentityService);
    }

    @Test
    void shouldThrowWhenSessionIsNotFoundInRedis() {
        var event = new APIGatewayProxyRequestEvent();
        event.setQueryStringParameters(Collections.emptyMap());
        event.setHeaders(Map.of(COOKIE, buildCookieString()));
        var expectedException =
                assertThrows(
                        RuntimeException.class,
                        () -> handler.handleRequest(event, context),
                        "Expected to throw exception");

        assertThat(expectedException.getMessage(), containsString("Session not found"));

        verifyNoInteractions(auditService);
    }

    @Test
    void shouldThrowWhenUserProfileNotFound() {
        usingValidSession();
        usingValidClientSession();
        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("code", AUTH_CODE.getValue());
        responseHeaders.put("state", STATE.getValue());
        when(dynamoClientService.getClient(CLIENT_ID.getValue()))
                .thenReturn(Optional.of(generateClientRegistry()));
        when(responseService.validateResponse(responseHeaders, SESSION_ID))
                .thenReturn(Optional.empty());
        when(dynamoService.getUserProfileFromEmail(TEST_EMAIL_ADDRESS))
                .thenReturn(Optional.empty());

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setQueryStringParameters(responseHeaders);
        event.setHeaders(Map.of(COOKIE, buildCookieString()));

        RuntimeException expectedException =
                assertThrows(
                        RuntimeException.class,
                        () -> handler.handleRequest(event, context),
                        "Expected to throw exception");

        assertThat(
                expectedException.getMessage(),
                equalTo("Email from session does not have a user profile"));

        verifyNoInteractions(auditService);
        verifyNoInteractions(dynamoIdentityService);
    }

    @Test
    void shouldRedirectToRpWhenAuthResponseContainsError() {
        var errorDescription = "redirect_uri param must be provided";
        usingValidSession();
        usingValidClientSession();
        var errorObject = new ErrorObject("invalid_request_redirect_uri", errorDescription);
        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("code", AUTH_CODE.getValue());
        responseHeaders.put("state", STATE.getValue());
        responseHeaders.put("error", errorObject.toString());
        when(dynamoClientService.getClient(CLIENT_ID.getValue()))
                .thenReturn(Optional.of(generateClientRegistry()));
        when(responseService.validateResponse(responseHeaders, SESSION_ID))
                .thenReturn(Optional.of(new ErrorObject(errorObject.getCode(), errorDescription)));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHeaders(Map.of(COOKIE, buildCookieString()));
        event.setQueryStringParameters(responseHeaders);

        var response = handler.handleRequest(event, context);

        var expectedErrorObject = new ErrorObject(OAuth2Error.ACCESS_DENIED_CODE, errorDescription);
        var expectedURI =
                new AuthenticationErrorResponse(
                                URI.create(REDIRECT_URI.toString()),
                                expectedErrorObject,
                                RP_STATE,
                                null)
                        .toURI()
                        .toString();

        assertThat(response, hasStatus(302));
        assertThat(response.getHeaders().get(ResponseHeaders.LOCATION), equalTo(expectedURI));

        verifyNoInteractions(ipvTokenService);
        verifyNoInteractions(auditService);
        verifyNoInteractions(dynamoIdentityService);
    }

    @Test
    void shouldThrowWhenClientRegistryIsNotFound() {
        usingValidSession();
        usingValidClientSession();
        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("code", AUTH_CODE.getValue());
        responseHeaders.put("state", STATE.getValue());
        when(dynamoClientService.getClient(CLIENT_ID.getValue())).thenReturn(Optional.empty());

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHeaders(Map.of(COOKIE, buildCookieString()));
        event.setQueryStringParameters(responseHeaders);

        RuntimeException expectedException =
                assertThrows(
                        RuntimeException.class,
                        () -> handler.handleRequest(event, context),
                        "Expected to throw exception");

        assertThat(
                expectedException.getMessage(),
                equalTo("Client registry not found with given clientId"));

        verifyNoInteractions(ipvTokenService);
        verifyNoInteractions(auditService);
        verifyNoInteractions(dynamoIdentityService);
    }

    @Test
    void shouldThrowWhenTokenResponseIsNotSuccessful() {
        var salt = "Mmc48imEuO5kkVW7NtXVtx5h0mbCTfXsqXdWvbRMzdw=".getBytes();
        var clientRegistry = generateClientRegistry();
        var userProfile = generateUserProfile();
        usingValidSession();
        usingValidClientSession();
        var unsuccessfulTokenResponse = new TokenErrorResponse(new ErrorObject("Error object"));
        var tokenRequest = mock(TokenRequest.class);
        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("code", AUTH_CODE.getValue());
        responseHeaders.put("state", STATE.getValue());
        when(dynamoClientService.getClient(CLIENT_ID.getValue()))
                .thenReturn(Optional.of(clientRegistry));
        when(responseService.validateResponse(responseHeaders, SESSION_ID))
                .thenReturn(Optional.empty());
        when(dynamoService.getUserProfileFromEmail(TEST_EMAIL_ADDRESS))
                .thenReturn(Optional.of(userProfile));
        when(dynamoService.getOrGenerateSalt(userProfile)).thenReturn(salt);
        when(ipvTokenService.constructTokenRequest(AUTH_CODE.getValue())).thenReturn(tokenRequest);
        when(ipvTokenService.sendTokenRequest(tokenRequest)).thenReturn(unsuccessfulTokenResponse);

        var event = new APIGatewayProxyRequestEvent();
        event.setQueryStringParameters(responseHeaders);
        event.setHeaders(Map.of(COOKIE, buildCookieString()));

        var expectedException =
                assertThrows(
                        RuntimeException.class,
                        () -> handler.handleRequest(event, context),
                        "Expected to throw exception");

        assertThat(
                expectedException.getMessage(),
                containsString("IPV TokenResponse was not successful"));

        verify(auditService)
                .submitAuditEvent(
                        IPVAuditableEvent.IPV_AUTHORISATION_RESPONSE_RECEIVED,
                        REQUEST_ID,
                        SESSION_ID,
                        CLIENT_ID.getValue(),
                        userProfile.getSubjectID(),
                        TEST_EMAIL_ADDRESS,
                        AuditService.UNKNOWN,
                        userProfile.getPhoneNumber(),
                        PERSISTENT_SESSION_ID);

        verify(auditService)
                .submitAuditEvent(
                        IPVAuditableEvent.IPV_UNSUCCESSFUL_TOKEN_RESPONSE_RECEIVED,
                        REQUEST_ID,
                        SESSION_ID,
                        CLIENT_ID.getValue(),
                        userProfile.getSubjectID(),
                        TEST_EMAIL_ADDRESS,
                        AuditService.UNKNOWN,
                        userProfile.getPhoneNumber(),
                        PERSISTENT_SESSION_ID);

        verifyNoMoreInteractions(auditService);
        verifyNoInteractions(dynamoIdentityService);
    }

    private APIGatewayProxyResponseEvent makeHandlerRequest(APIGatewayProxyRequestEvent event) {
        return handler.handleRequest(event, context);
    }

    private static String buildCookieString() {
        return format(
                "%s=%s.%s; Max-Age=%d; %s di-persistent-session-id=%s; Max-Age=34190000; Domain=auth.ida.digital.cabinet-office.gov.uk; Secure; HttpOnly;",
                "gs",
                SESSION_ID,
                CLIENT_SESSION_ID,
                3600,
                "Secure; HttpOnly;",
                PERSISTENT_SESSION_ID);
    }

    private void usingValidSession() {
        when(sessionService.readSessionFromRedis(SESSION_ID)).thenReturn(Optional.of(session));
    }

    private void usingValidClientSession() {
        when(clientSessionService.getClientSession(CLIENT_SESSION_ID))
                .thenReturn(Optional.of(clientSession));
    }

    private UserProfile generateUserProfile() {
        return new UserProfile()
                .setEmail(TEST_EMAIL_ADDRESS)
                .setEmailVerified(true)
                .setPhoneNumber("012345678902")
                .setPhoneNumberVerified(true)
                .setPublicSubjectID(PUBLIC_SUBJECT.getValue())
                .setSubjectID(SUBJECT.getValue());
    }

    private ClientRegistry generateClientRegistry() {
        return new ClientRegistry()
                .setClientID(CLIENT_ID.getValue())
                .setConsentRequired(false)
                .setClientName("test-client")
                .setRedirectUrls(singletonList(REDIRECT_URI.toString()))
                .setSectorIdentifierUri("https://test.com")
                .setSubjectType("pairwise");
    }

    public static AuthenticationRequest generateAuthRequest() {
        ResponseType responseType = new ResponseType(ResponseType.Value.CODE);
        Scope scope = new Scope();
        Nonce nonce = new Nonce();
        scope.add(OIDCScopeValue.OPENID);
        scope.add("phone");
        scope.add("email");
        return new AuthenticationRequest.Builder(responseType, scope, CLIENT_ID, REDIRECT_URI)
                .state(RP_STATE)
                .nonce(nonce)
                .build();
    }

    private void verifyAuditEvent(IPVAuditableEvent auditableEvent) {
        verify(auditService)
                .submitAuditEvent(
                        auditableEvent,
                        REQUEST_ID,
                        SESSION_ID,
                        CLIENT_ID.getValue(),
                        userProfile.getSubjectID(),
                        TEST_EMAIL_ADDRESS,
                        AuditService.UNKNOWN,
                        userProfile.getPhoneNumber(),
                        PERSISTENT_SESSION_ID);
    }

    private APIGatewayProxyRequestEvent getApiGatewayProxyRequestEvent(
            UserInfo userIdentityUserInfo) {
        var successfulTokenResponse =
                new AccessTokenResponse(new Tokens(new BearerAccessToken(), null));
        var tokenRequest = mock(TokenRequest.class);
        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("code", AUTH_CODE.getValue());
        responseHeaders.put("state", STATE.getValue());
        when(dynamoClientService.getClient(CLIENT_ID.getValue()))
                .thenReturn(Optional.of(clientRegistry));
        when(responseService.validateResponse(responseHeaders, SESSION_ID))
                .thenReturn(Optional.empty());
        when(dynamoService.getUserProfileFromEmail(TEST_EMAIL_ADDRESS))
                .thenReturn(Optional.of(userProfile));
        when(dynamoService.getOrGenerateSalt(userProfile)).thenReturn(salt);
        when(ipvTokenService.constructTokenRequest(AUTH_CODE.getValue())).thenReturn(tokenRequest);
        when(ipvTokenService.sendTokenRequest(tokenRequest)).thenReturn(successfulTokenResponse);
        when(ipvTokenService.sendIpvUserIdentityRequest(ArgumentMatchers.any()))
                .thenReturn(userIdentityUserInfo);

        var event = new APIGatewayProxyRequestEvent();
        event.setQueryStringParameters(responseHeaders);
        event.setHeaders(Map.of(COOKIE, buildCookieString()));
        return event;
    }
}
