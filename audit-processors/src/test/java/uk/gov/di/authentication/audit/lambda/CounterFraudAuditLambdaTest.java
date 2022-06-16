package uk.gov.di.authentication.audit.lambda;

import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.di.audit.AuditPayload.AuditEvent;
import uk.gov.di.audit.AuditPayload.AuditEvent.User;
import uk.gov.di.authentication.audit.configuration.TXMAConfiguration;
import uk.gov.di.authentication.shared.services.ConfigurationService;
import uk.gov.di.authentication.shared.services.KmsConnectionService;
import uk.gov.di.authentication.sharedtest.logging.CaptureLoggingExtension;

import java.nio.ByteBuffer;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.di.authentication.sharedtest.logging.LogEventMatcher.doesNotHaveObjectMessageProperty;
import static uk.gov.di.authentication.sharedtest.logging.LogEventMatcher.hasObjectMessageProperty;

public class CounterFraudAuditLambdaTest {

    @RegisterExtension
    public final CaptureLoggingExtension logging =
            new CaptureLoggingExtension(CounterFraudAuditLambda.class);

    private final KmsConnectionService kms = mock(KmsConnectionService.class);
    private final ConfigurationService config = mock(ConfigurationService.class);
    private final TXMAConfiguration txmaConfiguration = mock(TXMAConfiguration.class);

    @BeforeEach
    public void setUp() {

        when(config.getAuditSigningKeyAlias()).thenReturn("key_alias");
        when(config.getAuditHmacSecret()).thenReturn("i-am-a-fake-hash-key");
        when(kms.validateSignature(any(ByteBuffer.class), any(ByteBuffer.class), eq("key_alias")))
                .thenReturn(true);
        when(txmaConfiguration.getObfuscationHMACSecret()).thenReturn(Optional.empty());
    }

    @Test
    void handlesRequestsAppropriately() {
        var handler = new CounterFraudAuditLambda(kms, config, txmaConfiguration);

        var payload =
                AuditEvent.newBuilder()
                        .setEventId("test-event-id")
                        .setRequestId("test-request-id")
                        .setSessionId("test-session-id")
                        .setClientId("test-client-id")
                        .setTimestamp("test-timestamp")
                        .setEventName("test-event-name")
                        .setPersistentSessionId("test-persistent-session-id")
                        .build();

        handler.handleAuditEvent(payload);

        LogEvent logEvent = logging.events().get(0);

        assertThat(logEvent, hasObjectMessageProperty("event-id", "test-event-id"));
        assertThat(logEvent, hasObjectMessageProperty("request-id", "test-request-id"));
        assertThat(logEvent, hasObjectMessageProperty("session-id", "test-session-id"));
        assertThat(logEvent, hasObjectMessageProperty("client-id", "test-client-id"));
        assertThat(logEvent, hasObjectMessageProperty("timestamp", "test-timestamp"));
        assertThat(logEvent, hasObjectMessageProperty("event-name", "test-event-name"));
        assertThat(
                logEvent,
                hasObjectMessageProperty("persistent-session-id", "test-persistent-session-id"));
    }

    @Test
    void shouldHashSensitiveFields() {
        var handler = new CounterFraudAuditLambda(kms, config, txmaConfiguration);

        var payload =
                AuditEvent.newBuilder()
                        .setUser(
                                User.newBuilder()
                                        .setId("test-id")
                                        .setEmail("test-example@digital.cabinet-office.gov.uk")
                                        .setPhoneNumber("test-phone-number")
                                        .setIpAddress("test-ip-address")
                                        .build())
                        .build();

        handler.handleAuditEvent(payload);

        LogEvent logEvent = logging.events().get(0);

        assertThat(
                logEvent,
                hasObjectMessageProperty(
                        "user.email",
                        "dbc2c80d5e663075eb736f52df8446c109878f1a27b9d2f7db634d4e64923c94"));
        assertThat(
                logEvent,
                hasObjectMessageProperty(
                        "user.id",
                        "fe3ad3ffe725ab111628ea3df4b04fb0fda486479fb621c8d4ac325c9e1ce91b"));
        assertThat(
                logEvent,
                hasObjectMessageProperty(
                        "user.phone",
                        "889340bac0d98dc4f74eeef79c907ea763f3915277d641176fa081d8f7b48cd7"));

        assertThat(logEvent, hasObjectMessageProperty("user.ip-address", "test-ip-address"));
    }

    @Test
    void shouldAdditionallyHashSensitiveFieldsWithTxmaKeyIfPresent() {
        var handler = new CounterFraudAuditLambda(kms, config, txmaConfiguration);
        when(txmaConfiguration.getObfuscationHMACSecret())
                .thenReturn(Optional.of("an-alternative-hash-key"));

        var payload =
                AuditEvent.newBuilder()
                        .setUser(
                                User.newBuilder()
                                        .setId("test-id")
                                        .setEmail("test-example@digital.cabinet-office.gov.uk")
                                        .setPhoneNumber("test-phone-number")
                                        .setIpAddress("test-ip-address")
                                        .build())
                        .build();

        handler.handleAuditEvent(payload);

        LogEvent logEvent = logging.events().get(0);

        assertThat(
                logEvent,
                hasObjectMessageProperty(
                        "user.email.txma",
                        "3d947d8758e25daf9ebd49f54a14b97eab971d648046b5d2cf123e1b93d4c81c"));
        assertThat(
                logEvent,
                hasObjectMessageProperty(
                        "user.id.txma",
                        "5a180d8cc70faf7a2ac589a991aa44b29a7849f12555fbaf7c37a804c45a45b2"));
        assertThat(
                logEvent,
                hasObjectMessageProperty(
                        "user.phone.txma",
                        "dbbb41f8142c64c9be09b201d80e927c26de47aa35ac98a82edee98afe5d3731"));

        assertThat(logEvent, hasObjectMessageProperty("user.ip-address", "test-ip-address"));
    }

    @Test
    void shouldAddExtensionsToPayload() {
        var handler = new CounterFraudAuditLambda(kms, config, txmaConfiguration);

        var payload =
                AuditEvent.newBuilder()
                        .putExtensions("key1", "value1")
                        .putExtensions("key2", "value2")
                        .build();

        handler.handleAuditEvent(payload);

        LogEvent logEvent = logging.events().get(0);

        assertThat(logEvent, hasObjectMessageProperty("extensions.key1", "value1"));
        assertThat(logEvent, hasObjectMessageProperty("extensions.key2", "value2"));
    }

    @Test
    void shouldNotHashMissingSensitiveFields_Id() {
        var handler = new CounterFraudAuditLambda(kms, config, txmaConfiguration);

        var payload =
                AuditEvent.newBuilder()
                        .setUser(
                                User.newBuilder()
                                        .setEmail("test-email")
                                        .setPhoneNumber("test-phone")
                                        .build())
                        .build();

        handler.handleAuditEvent(payload);

        LogEvent logEvent = logging.events().get(0);

        assertThat(logEvent, doesNotHaveObjectMessageProperty("user.id"));
    }

    @Test
    void shouldNotHashMissingSensitiveFields_Email() {
        var handler = new CounterFraudAuditLambda(kms, config, txmaConfiguration);

        var payload =
                AuditEvent.newBuilder()
                        .setUser(
                                User.newBuilder()
                                        .setId("test-id")
                                        .setPhoneNumber("test-phone")
                                        .build())
                        .build();

        handler.handleAuditEvent(payload);

        LogEvent logEvent = logging.events().get(0);

        assertThat(logEvent, doesNotHaveObjectMessageProperty("user.email"));
    }

    @Test
    void shouldNotHashMissingSensitiveFields_Phone() {
        var handler = new CounterFraudAuditLambda(kms, config, txmaConfiguration);

        var payload =
                AuditEvent.newBuilder()
                        .setUser(User.newBuilder().setId("test-id").setEmail("test-email").build())
                        .build();

        handler.handleAuditEvent(payload);

        LogEvent logEvent = logging.events().get(0);

        assertThat(logEvent, doesNotHaveObjectMessageProperty("user.phone"));
    }
}
