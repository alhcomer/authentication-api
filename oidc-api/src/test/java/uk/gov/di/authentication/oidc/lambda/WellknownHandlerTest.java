package uk.gov.di.authentication.oidc.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.nimbusds.oauth2.sdk.GrantType;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.openid.connect.sdk.claims.ClaimType;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import org.junit.jupiter.api.Test;
import uk.gov.di.authentication.shared.entity.ValidClaims;
import uk.gov.di.authentication.shared.services.ConfigurationService;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.di.authentication.sharedtest.matchers.APIGatewayProxyResponseEventMatcher.hasStatus;

class WellknownHandlerTest {

    private final Context context = mock(Context.class);
    private final ConfigurationService configService = mock(ConfigurationService.class);
    private WellknownHandler handler;

    @Test
    void shouldReturn200WhenRequestIsSuccessful() throws ParseException {
        when(configService.getOidcApiBaseURL()).thenReturn(Optional.of("http://localhost:8080"));
        handler = new WellknownHandler(configService);
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        APIGatewayProxyResponseEvent result = handler.handleRequest(event, context);

        URI expectedRegistrationURI = URI.create("http://localhost:8080/connect/register");
        String expectedTrustMarkURI = "http://localhost:8080/trustmark";

        assertThat(result, hasStatus(200));
        var metadata = OIDCProviderMetadata.parse(result.getBody());

        assertThat(metadata.getGrantTypes(), equalTo(List.of(GrantType.AUTHORIZATION_CODE)));
        assertThat(metadata.getClaimTypes(), equalTo(List.of(ClaimType.NORMAL)));
        assertThat(metadata.getRegistrationEndpointURI(), equalTo(expectedRegistrationURI));
        assertThat(metadata.supportsBackChannelLogout(), equalTo(true));
        assertThat(metadata.getCustomParameters().get("trustmarks"), equalTo(expectedTrustMarkURI));

        ValidClaims.allOneLoginClaims()
                .forEach(claim -> assertTrue(metadata.getClaims().contains(claim)));
    }

    @Test
    void shouldThrowExceptionWhenBaseUrlIsMissing() {
        when(configService.getOidcApiBaseURL()).thenReturn(Optional.empty());

        var expectedException =
                assertThrows(
                        RuntimeException.class,
                        () -> new WellknownHandler(configService),
                        "Expected to throw exception");

        assertThat(
                expectedException.getMessage(),
                equalTo("java.util.NoSuchElementException: No value present"));
    }
}
