package uk.gov.di.authentication.oidc.services;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.id.Subject;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCClaimsRequest;
import com.nimbusds.openid.connect.sdk.OIDCScopeValue;
import com.nimbusds.openid.connect.sdk.claims.ClaimRequirement;
import com.nimbusds.openid.connect.sdk.claims.ClaimsSetRequest;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.di.authentication.ipv.domain.IPVAuditableEvent;
import uk.gov.di.authentication.ipv.entity.IPVAuthorisationResponse;
import uk.gov.di.authentication.ipv.services.IPVAuthorisationService;
import uk.gov.di.authentication.shared.entity.ClientRegistry;
import uk.gov.di.authentication.shared.entity.Session;
import uk.gov.di.authentication.shared.helpers.ClientSubjectHelper;
import uk.gov.di.authentication.shared.helpers.SaltHelper;
import uk.gov.di.authentication.shared.serialization.Json;
import uk.gov.di.authentication.shared.services.AuditService;
import uk.gov.di.authentication.shared.services.AuthenticationService;
import uk.gov.di.authentication.shared.services.CloudwatchMetricsService;
import uk.gov.di.authentication.shared.services.ConfigurationService;
import uk.gov.di.authentication.shared.services.SerializationService;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static uk.gov.di.authentication.shared.services.AuditService.MetadataPair.pair;
import static uk.gov.di.authentication.sharedtest.helper.RequestEventHelper.contextWithSourceIp;
import static uk.gov.di.authentication.sharedtest.matchers.APIGatewayProxyResponseEventMatcher.hasStatus;

public class InitiateIPVAuthorisationServiceTest {
    private static final String CLIENT_SESSION_ID = "client-session-v1";
    private static final String PERSISTENT_SESSION_ID = "a-persistent-session-id";
    private static final String CLIENT_ID = "test-client-id";
    private static final String INTERNAL_SECTOR_URI = "https://ipv.account.gov.uk";
    private static final String SESSION_ID = "a-session-id";
    private static final String IPV_CLIENT_ID = "ipv-client-id";
    private static final URI REDIRECT_URI = URI.create("http://localhost/oidc/redirect");
    private static final String LANDING_PAGE_URL = "https//test.account.gov.uk/landingPage";
    private static final URI IPV_AUTHORISATION_URI = URI.create("http://localhost/ipv/authorize");
    private static final String ENVIRONMENT = "test-environment";
    private static final String EMAIL_ADDRESS = "test@test.com";
    private static final String SUBJECT_ID = new Subject("subject-id-3").getValue();
    private static final String RP_PAIRWISE_ID = "urn:fdc:gov.uk:2022:dkjfshsdkjh";
    private static final String IP_ADDRESS = "123.123.123.123";

    private final ConfigurationService configService = mock(ConfigurationService.class);
    private final AuthenticationService authenticationService = mock(AuthenticationService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final IPVAuthorisationService authorisationService =
            mock(IPVAuthorisationService.class);
    private final CloudwatchMetricsService cloudwatchMetricsService =
            mock(CloudwatchMetricsService.class);
    private InitiateIPVAuthorisationService initiateAuthorisationService;
    private APIGatewayProxyRequestEvent event;
    private final ClaimsSetRequest.Entry nameEntry =
            new ClaimsSetRequest.Entry("name").withClaimRequirement(ClaimRequirement.ESSENTIAL);
    private final ClaimsSetRequest.Entry birthDateEntry =
            new ClaimsSetRequest.Entry("birthdate")
                    .withClaimRequirement(ClaimRequirement.VOLUNTARY);
    private final ClaimsSetRequest claimsSetRequest =
            new ClaimsSetRequest().add(nameEntry).add(birthDateEntry);
    private final String expectedCommonSubject =
            ClientSubjectHelper.calculatePairwiseIdentifier(
                    SUBJECT_ID, "test.account.gov.uk", SaltHelper.generateNewSalt());
    private final AuthenticationRequest authenticationRequest = mock(AuthenticationRequest.class);
    private final UserInfo userInfo = generateUserInfo();
    private final Session session =
            new Session(SESSION_ID)
                    .setEmailAddress(EMAIL_ADDRESS)
                    .setInternalCommonSubjectIdentifier(expectedCommonSubject);
    private final ClientRegistry client = generateClientRegistry();

    public InitiateIPVAuthorisationServiceTest() throws com.nimbusds.oauth2.sdk.ParseException {}

    @BeforeEach
    void setup() {
        initiateAuthorisationService =
                new InitiateIPVAuthorisationService(
                        configService,
                        authenticationService,
                        auditService,
                        authorisationService,
                        cloudwatchMetricsService);

        event = new APIGatewayProxyRequestEvent();
        event.setRequestContext(contextWithSourceIp(IP_ADDRESS));

        when(configService.getIPVAuthorisationClientId()).thenReturn(IPV_CLIENT_ID);
        when(configService.getInternalSectorUri()).thenReturn(INTERNAL_SECTOR_URI);
        when(configService.isIdentityEnabled()).thenReturn(true);
        when(configService.getIPVAuthorisationURI()).thenReturn(IPV_AUTHORISATION_URI);
        when(configService.getEnvironment()).thenReturn(ENVIRONMENT);
    }

    @Test
    void shouldThrowWhenIdentityIsNotEnabled() {
        when(configService.isIdentityEnabled()).thenReturn(false);

        var exception =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                initiateAuthorisationService.sendRequestToIPV(
                                        event,
                                        authenticationRequest,
                                        userInfo,
                                        session,
                                        client,
                                        CLIENT_ID,
                                        CLIENT_SESSION_ID,
                                        PERSISTENT_SESSION_ID),
                        "Expected to throw exception");

        assertThat(exception.getMessage(), equalTo("Identity is not enabled"));
        verifyNoInteractions(cloudwatchMetricsService);
    }

    @Test
    void shouldReturn200AndRedirectURIWithClaims()
            throws JOSEException, ParseException, Json.JsonException {
        var encryptedJWT = createEncryptedJWT();
        when(authorisationService.constructRequestJWT(
                        any(State.class),
                        any(Scope.class),
                        any(Subject.class),
                        any(),
                        eq(CLIENT_SESSION_ID),
                        anyString()))
                .thenReturn(encryptedJWT);

        var response =
                initiateAuthorisationService.sendRequestToIPV(
                        event,
                        createAuthenticationRequest(),
                        userInfo,
                        session,
                        client,
                        CLIENT_ID,
                        CLIENT_SESSION_ID,
                        PERSISTENT_SESSION_ID);
        var body =
                SerializationService.getInstance()
                        .readValue(response.getBody(), IPVAuthorisationResponse.class);

        assertThat(response, hasStatus(200));
        assertThat(body.getRedirectUri(), startsWith(IPV_AUTHORISATION_URI.toString()));
        assertThat(
                splitQuery(body.getRedirectUri()).get("request"),
                equalTo(encryptedJWT.serialize()));
        verify(authorisationService).storeState(eq(session.getSessionId()), any(State.class));

        verify(auditService)
                .submitAuditEvent(
                        IPVAuditableEvent.IPV_AUTHORISATION_REQUESTED,
                        CLIENT_SESSION_ID,
                        SESSION_ID,
                        CLIENT_ID,
                        expectedCommonSubject,
                        EMAIL_ADDRESS,
                        IP_ADDRESS,
                        AuditService.UNKNOWN,
                        PERSISTENT_SESSION_ID,
                        pair("clientLandingPageUrl", LANDING_PAGE_URL),
                        pair("rpPairwiseId", RP_PAIRWISE_ID));
        verify(cloudwatchMetricsService)
                .incrementCounter("IPVHandoff", Map.of("Environment", ENVIRONMENT));
    }

    private EncryptedJWT createEncryptedJWT() throws JOSEException, ParseException {
        var ecSigningKey =
                new ECKeyGenerator(Curve.P_256)
                        .keyID("key-id")
                        .algorithm(JWSAlgorithm.ES256)
                        .generate();
        var ecdsaSigner = new ECDSASigner(ecSigningKey);
        var jwtClaimsSet =
                new JWTClaimsSet.Builder()
                        .claim("redirect_uri", "REDIRECT_URI")
                        .claim("response_type", ResponseType.CODE.toString())
                        .claim("client_id", "CLIENT_ID")
                        .claim("govuk_signin_journey_id", CLIENT_SESSION_ID)
                        .issuer("CLIENT_ID")
                        .build();
        var jwsHeader = new JWSHeader(JWSAlgorithm.ES256);
        var signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
        signedJWT.sign(ecdsaSigner);
        var rsaEncryptionKey =
                new RSAKeyGenerator(2048).keyID("encrytion-key-id").generate().toRSAPublicKey();
        var jweObject =
                new JWEObject(
                        new JWEHeader.Builder(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM)
                                .contentType("JWT")
                                .build(),
                        new Payload(signedJWT));
        jweObject.encrypt(new RSAEncrypter(rsaEncryptionKey));
        return EncryptedJWT.parse(jweObject.serialize());
    }

    private UserInfo generateUserInfo() throws com.nimbusds.oauth2.sdk.ParseException {
        String jsonString =
                String.format(
                        "{\n"
                                + "                \"sub\": \"urn:fdc:gov.uk:2022:jdgfhgfsdret\",\n"
                                + "                \"legacy_subject_id\": \"odkjfshsdkjhdkjfshsdkjhdkjfshsdkjh\",\n"
                                + "                \"public_subject_id\": \"pdkjfshsdkjhdkjfshsdkjhdkjfshsdkjh\",\n"
                                + "                \"local_account_id\": \"dkjfshsdkjhdkjfshsdkjhdkjfshsdkjh\",\n"
                                + "                \"rp_pairwise_id\": \"%s\",\n"
                                + "                \"email\": \"%s\",\n"
                                + "                \"email_verified\": true,\n"
                                + "                \"phone_number\": \"007492837401\",\n"
                                + "                \"phone_number_verified\": true,\n"
                                + "                \"new_account\": \"true\",\n"
                                + "                \"salt\": \"\" }",
                        RP_PAIRWISE_ID, EMAIL_ADDRESS);
        return UserInfo.parse(jsonString);
    }

    private ClientRegistry generateClientRegistry() {
        return new ClientRegistry()
                .withRedirectUrls(singletonList(REDIRECT_URI.toString()))
                .withClientID(CLIENT_ID)
                .withContacts(singletonList("joe.bloggs@digital.cabinet-office.gov.uk"))
                .withPublicKey(null)
                .withSectorIdentifierUri("http://sector-identifier")
                .withScopes(singletonList("openid"))
                .withCookieConsentShared(true)
                .withSubjectType("pairwise")
                .withLandingPageUrl(LANDING_PAGE_URL);
    }

    public static Map<String, String> splitQuery(String stringUrl) {
        URI uri = URI.create(stringUrl);
        Map<String, String> query_pairs = new LinkedHashMap<>();
        String query = uri.getQuery();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            query_pairs.put(
                    URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
        }
        return query_pairs;
    }

    private AuthenticationRequest createAuthenticationRequest() {
        Scope scope = new Scope();
        scope.add(OIDCScopeValue.OPENID);
        var oidcClaimsRequest = new OIDCClaimsRequest().withUserInfoClaimsRequest(claimsSetRequest);
        return new AuthenticationRequest.Builder(
                        new ResponseType(ResponseType.Value.CODE),
                        scope,
                        new ClientID(CLIENT_ID),
                        REDIRECT_URI)
                .state(new State())
                .nonce(new Nonce())
                .claims(oidcClaimsRequest)
                .build();
    }
}
