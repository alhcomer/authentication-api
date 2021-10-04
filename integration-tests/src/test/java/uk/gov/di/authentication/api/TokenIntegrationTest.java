package uk.gov.di.authentication.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.GrantType;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.PrivateKeyJWT;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.id.Subject;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.oauth2.sdk.util.JSONObjectUtils;
import com.nimbusds.oauth2.sdk.util.URLUtils;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCScopeValue;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.minidev.json.JSONObject;
import org.junit.jupiter.api.Test;
import uk.gov.di.authentication.helpers.DynamoHelper;
import uk.gov.di.authentication.helpers.KeyPairHelper;
import uk.gov.di.authentication.helpers.KmsHelper;
import uk.gov.di.authentication.helpers.RedisHelper;
import uk.gov.di.authentication.shared.entity.ClientConsent;
import uk.gov.di.authentication.shared.entity.ServiceType;
import uk.gov.di.authentication.shared.entity.TokenStore;
import uk.gov.di.authentication.shared.entity.ValidScopes;

import java.net.URI;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TokenIntegrationTest extends IntegrationTestEndpoints {

    private static final String TOKEN_ENDPOINT = "/token";
    private static final String TEST_EMAIL = "joe.bloggs@digital.cabinet-office.gov.uk";
    private static final String CLIENT_ID = "test-id";
    private static final String REFRESH_TOKEN_PREFIX = "REFRESH_TOKEN:";
    private static final String REDIRECT_URI = "http://localhost/redirect";

    @Test
    public void shouldCallTokenResourceAndReturnAccessAndRefreshToken()
            throws JOSEException, ParseException {
        KeyPair keyPair = KeyPairHelper.GENERATE_RSA_KEY_PAIR();
        Scope scope =
                new Scope(
                        OIDCScopeValue.OPENID.getValue(), OIDCScopeValue.OFFLINE_ACCESS.getValue());
        setUpDynamo(keyPair, scope, new Subject());
        Response response = generateTokenRequest(keyPair, scope);

        assertEquals(200, response.getStatus());
        JSONObject jsonResponse = JSONObjectUtils.parse(response.readEntity(String.class));
        assertNotNull(
                TokenResponse.parse(jsonResponse)
                        .toSuccessResponse()
                        .getTokens()
                        .getRefreshToken());
        assertNotNull(
                TokenResponse.parse(jsonResponse)
                        .toSuccessResponse()
                        .getTokens()
                        .getBearerAccessToken());
    }

    @Test
    public void shouldCallTokenResourceAndOnlyReturnAccessTokenWithoutOfflineAccessScope()
            throws JOSEException, ParseException {
        KeyPair keyPair = KeyPairHelper.GENERATE_RSA_KEY_PAIR();
        Scope scope = new Scope(OIDCScopeValue.OPENID.getValue());
        setUpDynamo(keyPair, scope, new Subject());
        Response response = generateTokenRequest(keyPair, scope);

        assertEquals(200, response.getStatus());
        JSONObject jsonResponse = JSONObjectUtils.parse(response.readEntity(String.class));
        assertNull(
                TokenResponse.parse(jsonResponse)
                        .toSuccessResponse()
                        .getTokens()
                        .getRefreshToken());
        assertNotNull(
                TokenResponse.parse(jsonResponse)
                        .toSuccessResponse()
                        .getTokens()
                        .getBearerAccessToken());
    }

    @Test
    public void shouldCallTokenResourceWithRefreshTokenGrantAndReturn200()
            throws JOSEException, JsonProcessingException {
        Scope scope =
                new Scope(
                        OIDCScopeValue.OPENID, OIDCScopeValue.EMAIL, OIDCScopeValue.OFFLINE_ACCESS);
        Subject publicSubject = new Subject();
        Subject internalSubject = new Subject();
        KeyPair keyPair = KeyPairHelper.GENERATE_RSA_KEY_PAIR();
        setUpDynamo(keyPair, scope, internalSubject);
        SignedJWT signedJWT = generateSignedRefreshToken(scope, publicSubject);
        RefreshToken refreshToken = new RefreshToken(signedJWT.serialize());
        TokenStore tokenStore = new TokenStore(refreshToken.getValue(), internalSubject.getValue());
        RedisHelper.addToRedis(
                REFRESH_TOKEN_PREFIX + CLIENT_ID + "." + publicSubject.getValue(),
                new ObjectMapper().writeValueAsString(tokenStore),
                900L);
        PrivateKey privateKey = keyPair.getPrivate();
        PrivateKeyJWT privateKeyJWT =
                new PrivateKeyJWT(
                        new ClientID(CLIENT_ID),
                        URI.create(ROOT_RESOURCE_URL + TOKEN_ENDPOINT),
                        JWSAlgorithm.RS256,
                        (RSAPrivateKey) privateKey,
                        null,
                        null);
        Map<String, List<String>> customParams = new HashMap<>();
        customParams.put(
                "grant_type", Collections.singletonList(GrantType.REFRESH_TOKEN.getValue()));
        customParams.put("client_id", Collections.singletonList(CLIENT_ID));
        customParams.put("refresh_token", Collections.singletonList(refreshToken.getValue()));
        Map<String, List<String>> privateKeyParams = privateKeyJWT.toParameters();
        privateKeyParams.putAll(customParams);
        Client client = ClientBuilder.newClient();
        WebTarget webTarget = client.target(ROOT_RESOURCE_URL + TOKEN_ENDPOINT);
        Invocation.Builder invocationBuilder = webTarget.request(MediaType.TEXT_PLAIN);
        String requestParams = URLUtils.serializeParameters(privateKeyParams);
        Response response =
                invocationBuilder.post(Entity.entity(requestParams, MediaType.TEXT_PLAIN));

        //  Commented out due to same reason as LogoutIntegration test. It's an issue with KSM
        // running inside localstack which causes the Caused by:
        // java.security.NoSuchAlgorithmException: EC KeyFactory not available error.
        //        assertEquals(200, response.getStatus());
        //        JSONObject jsonResponse =
        // JSONObjectUtils.parse(response.readEntity(String.class));
        //        assertNotNull(
        //                TokenResponse.parse(jsonResponse)
        //                        .toSuccessResponse()
        //                        .getTokens()
        //                        .getRefreshToken());
        //        assertNotNull(
        //                TokenResponse.parse(jsonResponse)
        //                        .toSuccessResponse()
        //                        .getTokens()
        //                        .getBearerAccessToken());
    }

    private SignedJWT generateSignedRefreshToken(Scope scope, Subject publicSubject) {
        LocalDateTime localDateTime = LocalDateTime.now().plusMinutes(60);
        Date expiryDate = Date.from(localDateTime.atZone(ZoneId.of("UTC")).toInstant());
        JWTClaimsSet claimsSet =
                new JWTClaimsSet.Builder()
                        .claim("scope", scope.toStringList())
                        .issuer("issuer-id")
                        .expirationTime(expiryDate)
                        .issueTime(
                                Date.from(LocalDateTime.now().atZone(ZoneId.of("UTC")).toInstant()))
                        .claim("client_id", CLIENT_ID)
                        .subject(publicSubject.getValue())
                        .jwtID(UUID.randomUUID().toString())
                        .build();
        return KmsHelper.signAccessToken(claimsSet);
    }

    private void setUpDynamo(KeyPair keyPair, Scope scope, Subject internalSubject) {
        DynamoHelper.registerClient(
                CLIENT_ID,
                "test-client",
                singletonList(REDIRECT_URI),
                singletonList(TEST_EMAIL),
                scope.toStringList(),
                Base64.getMimeEncoder().encodeToString(keyPair.getPublic().getEncoded()),
                singletonList("http://localhost/post-logout-redirect"),
                String.valueOf(ServiceType.MANDATORY),
                "https://test.com",
                "public");
        DynamoHelper.signUp(TEST_EMAIL, "password-1", internalSubject);
        Set<String> claims = ValidScopes.getClaimsForListOfScopes(scope.toStringList());
        ClientConsent clientConsent =
                new ClientConsent(
                        CLIENT_ID, claims, LocalDateTime.now(ZoneId.of("UTC")).toString());
        DynamoHelper.updateConsent(TEST_EMAIL, clientConsent);
    }

    private AuthenticationRequest generateAuthRequest(Scope scope) {
        ResponseType responseType = new ResponseType(ResponseType.Value.CODE);
        State state = new State();
        Nonce nonce = new Nonce();
        return new AuthenticationRequest.Builder(
                        responseType,
                        scope,
                        new ClientID(CLIENT_ID),
                        URI.create("http://localhost/redirect"))
                .state(state)
                .nonce(nonce)
                .build();
    }

    private Response generateTokenRequest(KeyPair keyPair, Scope scope) throws JOSEException {
        PrivateKey privateKey = keyPair.getPrivate();
        PrivateKeyJWT privateKeyJWT =
                new PrivateKeyJWT(
                        new ClientID(CLIENT_ID),
                        URI.create(ROOT_RESOURCE_URL + TOKEN_ENDPOINT),
                        JWSAlgorithm.RS256,
                        (RSAPrivateKey) privateKey,
                        null,
                        null);
        String code = new AuthorizationCode().toString();
        RedisHelper.addAuthCodeAndCreateClientSession(
                code, "a-client-session-id", TEST_EMAIL, generateAuthRequest(scope).toParameters());
        Map<String, List<String>> customParams = new HashMap<>();
        customParams.put(
                "grant_type", Collections.singletonList(GrantType.AUTHORIZATION_CODE.getValue()));
        customParams.put("client_id", Collections.singletonList(CLIENT_ID));
        customParams.put("code", Collections.singletonList(code));
        customParams.put("redirect_uri", Collections.singletonList(REDIRECT_URI));
        Map<String, List<String>> privateKeyParams = privateKeyJWT.toParameters();
        privateKeyParams.putAll(customParams);
        Client client = ClientBuilder.newClient();
        WebTarget webTarget = client.target(ROOT_RESOURCE_URL + TOKEN_ENDPOINT);
        Invocation.Builder invocationBuilder = webTarget.request(MediaType.TEXT_PLAIN);
        String requestParams = URLUtils.serializeParameters(privateKeyParams);
        return invocationBuilder.post(Entity.entity(requestParams, MediaType.TEXT_PLAIN));
    }
}
