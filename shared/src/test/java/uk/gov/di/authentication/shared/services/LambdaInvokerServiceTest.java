package uk.gov.di.authentication.shared.services;

import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import uk.gov.di.authentication.shared.serialization.Json;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LambdaInvokerServiceTest {

    ConfigurationService configurationService = mock(ConfigurationService.class);

    LambdaClient lambdaClient = mock(LambdaClient.class);

    ScheduledEvent scheduledEvent = mock(ScheduledEvent.class);

    protected final Json objectMapper = SerializationService.getInstance();

    @Test
    void shouldInvokeTheLambdaWithTheGivenScheduledEvent() throws Json.JsonException {
        var functionName = "BULK_USER_EMAIL_AUDIENCE_LOADER";
        when(configurationService.getBulkEmailLoaderLambdaName()).thenReturn(functionName);

        var payload =
                SdkBytes.fromByteArray(
                        objectMapper
                                .writeValueAsString(scheduledEvent)
                                .getBytes(StandardCharsets.UTF_8));
        InvokeRequest invokeRequest =
                InvokeRequest.builder().functionName(functionName).payload(payload).build();
        LambdaInvokerService lambdaInvokerService =
                new LambdaInvokerService(configurationService, lambdaClient);

        lambdaInvokerService.invokeWithPayload(scheduledEvent);

        verify(lambdaClient).invoke(invokeRequest);
    }

    @Test
    void shouldThrowErrorWhenLambdaNameNotSetInEnvironment() throws Json.JsonException {
        when(configurationService.getBulkEmailLoaderLambdaName()).thenReturn("");
        LambdaInvokerService lambdaInvokerService =
                new LambdaInvokerService(configurationService, lambdaClient);

        assertThrows(
                RuntimeException.class,
                () -> lambdaInvokerService.invokeWithPayload(scheduledEvent),
                "BULK_USER_EMAIL_AUDIENCE_LOADER_LAMBDA_NAME environment variable not set");
    }
}
