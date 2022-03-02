package uk.gov.di.authentication.frontendapi.services;

import com.nimbusds.oauth2.sdk.ParseException;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.di.authentication.shared.entity.ClientRegistry;
import uk.gov.di.authentication.shared.entity.ClientSession;
import uk.gov.di.authentication.shared.entity.Session;
import uk.gov.di.authentication.shared.entity.UserProfile;
import uk.gov.di.authentication.shared.entity.VectorOfTrust;
import uk.gov.di.authentication.shared.services.DynamoClientService;
import uk.gov.di.authentication.shared.services.DynamoService;
import uk.gov.di.authentication.shared.state.UserContext;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.di.authentication.sharedtest.helper.JsonArrayHelper.jsonArrayOf;

class StartServiceTest {

    private final DynamoClientService dynamoClientService = mock(DynamoClientService.class);
    private final DynamoService dynamoService = mock(DynamoService.class);
    private static final URI REDIRECT_URI = URI.create("http://localhost/redirect");
    private static final String EMAIL = "joe.bloggs@example.com";
    private static final ClientID CLIENT_ID = new ClientID("client-id");
    private static final String CLIENT_NAME = "test-client";
    private static final Session SESSION = new Session("a-session-id").setEmailAddress(EMAIL);
    private static final Scope SCOPES =
            new Scope(OIDCScopeValue.OPENID, OIDCScopeValue.EMAIL, OIDCScopeValue.OFFLINE_ACCESS);
    private StartService startService;

    @BeforeEach
    void setup() {
        startService = new StartService(dynamoClientService, dynamoService);
    }

    @Test
    void shouldCreateUserContextFromSessionAndClientSession() {
        when(dynamoClientService.getClient(CLIENT_ID.getValue()))
                .thenReturn(
                        Optional.of(
                                generateClientRegistry(
                                        REDIRECT_URI.toString(), CLIENT_ID.getValue())));
        when(dynamoService.getUserProfileByEmailMaybe(EMAIL))
                .thenReturn(Optional.of(mock(UserProfile.class)));
        var authRequest =
                new AuthenticationRequest.Builder(
                                new ResponseType(ResponseType.Value.CODE),
                                SCOPES,
                                CLIENT_ID,
                                REDIRECT_URI)
                        .state(new State())
                        .nonce(new Nonce())
                        .build();
        var clientSession =
                new ClientSession(
                        authRequest.toParameters(), LocalDateTime.now(), mock(VectorOfTrust.class));
        var userContext = startService.buildUserContext(SESSION, clientSession);

        assertThat(userContext.getSession(), equalTo(SESSION));
        assertThat(userContext.getClientSession(), equalTo(clientSession));
    }

    @ParameterizedTest
    @MethodSource("userStartInfo")
    void shouldCreateUserStartInfo(
            String vtr,
            boolean isIdentityRequired,
            boolean isUpliftRequired,
            boolean clientConsentRequired,
            boolean isConsentRequired) {
        var userContext = buildUserContext(vtr, clientConsentRequired, false);
        var userStartInfo = startService.buildUserStartInfo(userContext);

        assertThat(userStartInfo.isUpliftRequired(), equalTo(isUpliftRequired));
        assertThat(userStartInfo.isIdentityRequired(), equalTo(isIdentityRequired));
        assertThat(userStartInfo.isConsentRequired(), equalTo(isConsentRequired));
    }

    @ParameterizedTest
    @MethodSource("clientStartInfo")
    void shouldCreateClientStartInfo(boolean cookieConsentShared) throws ParseException {
        var userContext = buildUserContext(jsonArrayOf("Cl.Cm"), false, cookieConsentShared);

        var clientStartInfo = startService.buildClientStartInfo(userContext);

        assertThat(clientStartInfo.getCookieConsentShared(), equalTo(cookieConsentShared));
        assertThat(clientStartInfo.getClientName(), equalTo(CLIENT_NAME));
        assertThat(clientStartInfo.getScopes(), equalTo(SCOPES.toStringList()));
    }

    private static Stream<Arguments> userStartInfo() {
        return Stream.of(
                Arguments.of(jsonArrayOf("Cl.Cm"), false, false, false, false),
                Arguments.of(jsonArrayOf("P2.Cl.Cm"), true, false, true, true));
    }

    private static Stream<Boolean> clientStartInfo() {
        return Stream.of(false, true);
    }

    private ClientRegistry generateClientRegistry(String redirectURI, String clientID) {
        return new ClientRegistry()
                .setRedirectUrls(singletonList(redirectURI))
                .setClientID(clientID)
                .setContacts(singletonList("joe.bloggs@digital.cabinet-office.gov.uk"))
                .setPublicKey(null)
                .setScopes(singletonList("openid"));
    }

    private UserContext buildUserContext(
            String vtrValue, boolean consentRequired, boolean cookieConsentShared) {
        var authRequest =
                new AuthenticationRequest.Builder(
                                new ResponseType(ResponseType.Value.CODE),
                                SCOPES,
                                CLIENT_ID,
                                REDIRECT_URI)
                        .state(new State())
                        .nonce(new Nonce())
                        .customParameter("vtr", vtrValue)
                        .build();
        var clientSession =
                new ClientSession(
                        authRequest.toParameters(),
                        LocalDateTime.now(),
                        VectorOfTrust.getDefaults());
        var clientRegistry =
                new ClientRegistry()
                        .setClientID(CLIENT_ID.getValue())
                        .setClientName(CLIENT_NAME)
                        .setConsentRequired(consentRequired)
                        .setCookieConsentShared(cookieConsentShared);
        return UserContext.builder(SESSION)
                .withClientSession(clientSession)
                .withClient(clientRegistry)
                .build();
    }
}