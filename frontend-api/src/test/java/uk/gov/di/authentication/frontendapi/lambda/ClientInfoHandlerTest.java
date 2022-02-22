package uk.gov.di.authentication.frontendapi.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
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
import uk.gov.di.authentication.frontendapi.domain.FrontendAuditableEvent;
import uk.gov.di.authentication.frontendapi.entity.ClientInfoResponse;
import uk.gov.di.authentication.shared.entity.ClientRegistry;
import uk.gov.di.authentication.shared.entity.ClientSession;
import uk.gov.di.authentication.shared.entity.ErrorResponse;
import uk.gov.di.authentication.shared.entity.Session;
import uk.gov.di.authentication.shared.entity.VectorOfTrust;
import uk.gov.di.authentication.shared.helpers.PersistentIdHelper;
import uk.gov.di.authentication.shared.services.AuditService;
import uk.gov.di.authentication.shared.services.ClientService;
import uk.gov.di.authentication.shared.services.ClientSessionService;
import uk.gov.di.authentication.shared.services.SessionService;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.di.authentication.sharedtest.helper.RequestEventHelper.contextWithSourceIp;
import static uk.gov.di.authentication.sharedtest.matchers.APIGatewayProxyResponseEventMatcher.hasBody;
import static uk.gov.di.authentication.sharedtest.matchers.APIGatewayProxyResponseEventMatcher.hasStatus;

public class ClientInfoHandlerTest {

    public static final String TEST_CLIENT_ID = "test_client_id";
    public static final String TEST_CLIENT_NAME = "test_client_name";
    public static final String UNKNOWN_TEST_CLIENT_ID = "unknown_test_client_id";
    public static final String CLIENT_SESSION_ID_HEADER = "Client-Session-Id";
    public static final String KNOWN_CLIENT_SESSION_ID = "known-client-session-id";
    public static final String UNKNOWN_CLIENT_SESSION_ID = "unknown-client-session-id";

    private ClientInfoHandler handler;
    private ClientRegistry clientRegistry;
    private final Context context = mock(Context.class);
    private final ClientSessionService clientSessionService = mock(ClientSessionService.class);
    private final ClientService clientService = mock(ClientService.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final AuditService auditService = mock(AuditService.class);

    @BeforeEach
    public void beforeEach() {
        when(context.getAwsRequestId()).thenReturn("aws-session-id");

        handler =
                new ClientInfoHandler(
                        clientSessionService, clientService, sessionService, auditService);
        clientRegistry = new ClientRegistry();
        clientRegistry.setClientID(TEST_CLIENT_ID);
        clientRegistry.setCookieConsentShared(true);
        clientRegistry.setClientName(TEST_CLIENT_NAME);
        when(clientService.getClient(TEST_CLIENT_ID)).thenReturn(Optional.of(clientRegistry));
        when(clientService.getClient(UNKNOWN_TEST_CLIENT_ID)).thenReturn(Optional.empty());
        when(sessionService.getSessionFromRequestHeaders(any()))
                .thenReturn(Optional.of(new Session("session-id")));
    }

    @Test
    public void shouldReturn200WithClientInfoResponseForKnownClientSessionId()
            throws JsonProcessingException {
        usingValidClientSession();
        String persistentId = "some-persistent-id-value";
        Map<String, String> headers = new HashMap<>();
        headers.put(PersistentIdHelper.PERSISTENT_ID_HEADER_NAME, persistentId);
        headers.put(CLIENT_SESSION_ID_HEADER, KNOWN_CLIENT_SESSION_ID);
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHeaders(headers);
        event.setRequestContext(contextWithSourceIp("123.123.123.123"));
        APIGatewayProxyResponseEvent result = handler.handleRequest(event, context);

        assertThat(result, hasStatus(200));

        ClientInfoResponse response =
                new ObjectMapper().readValue(result.getBody(), ClientInfoResponse.class);

        assertEquals(response.getClientId(), TEST_CLIENT_ID);
        assertEquals(response.getClientName(), TEST_CLIENT_NAME);
        assertThat(response.getCookieConsentShared(), equalTo(true));

        verify(auditService)
                .submitAuditEvent(
                        FrontendAuditableEvent.CLIENT_INFO_FOUND,
                        "aws-session-id",
                        "session-id",
                        TEST_CLIENT_ID,
                        auditService.UNKNOWN,
                        auditService.UNKNOWN,
                        "123.123.123.123",
                        persistentId,
                        AuditService.UNKNOWN);
    }

    @Test
    public void shouldReturn400ForUnknownClientSessionId() throws JsonProcessingException {
        usingInvalidClientSession();
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHeaders(Map.of(CLIENT_SESSION_ID_HEADER, UNKNOWN_CLIENT_SESSION_ID));
        APIGatewayProxyResponseEvent result = handler.handleRequest(event, context);

        assertThat(result, hasStatus(400));

        String expectedResponse = new ObjectMapper().writeValueAsString(ErrorResponse.ERROR_1018);
        assertThat(result, hasBody(expectedResponse));

        verifyNoInteractions(auditService);
    }

    @Test
    public void shouldReturn403ForUnknownClientInRegistry() throws JsonProcessingException {
        usingUnregisteredClientSession();
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHeaders(Map.of(CLIENT_SESSION_ID_HEADER, KNOWN_CLIENT_SESSION_ID));
        APIGatewayProxyResponseEvent result = handler.handleRequest(event, context);

        assertThat(result, hasStatus(403));

        String expectedResponse = new ObjectMapper().writeValueAsString(ErrorResponse.ERROR_1015);
        assertThat(result, hasBody(expectedResponse));

        verifyNoInteractions(auditService);
    }

    private void usingValidClientSession() {
        when(clientSessionService.getClientSessionFromRequestHeaders(anyMap()))
                .thenReturn(getClientSession(TEST_CLIENT_ID));
    }

    private void usingInvalidClientSession() {
        when(clientSessionService.getClientSessionFromRequestHeaders(anyMap()))
                .thenReturn(Optional.empty());
    }

    private void usingUnregisteredClientSession() {
        when(clientSessionService.getClientSessionFromRequestHeaders(anyMap()))
                .thenReturn(getClientSession(UNKNOWN_TEST_CLIENT_ID));
    }

    private Optional<ClientSession> getClientSession(String clientId) {
        ResponseType responseType = new ResponseType(ResponseType.Value.CODE);
        Scope scope = new Scope();
        scope.add(OIDCScopeValue.OPENID);
        State state = new State();
        AuthenticationRequest authRequest =
                new AuthenticationRequest.Builder(
                                responseType,
                                scope,
                                new ClientID(clientId),
                                URI.create("http://localhost/redirect"))
                        .state(state)
                        .nonce(new Nonce())
                        .build();
        return Optional.of(
                new ClientSession(authRequest.toParameters(), null, mock(VectorOfTrust.class)));
    }
}
