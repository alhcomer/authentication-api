package uk.gov.di.services;

import uk.gov.di.entity.NotificationType;

import java.net.URI;
import java.util.Optional;

public class ConfigurationService {

    public Optional<String> getBaseURL() {
        return Optional.ofNullable(System.getenv("BASE_URL"));
    }

    public URI getLoginURI() {
        return URI.create(System.getenv("LOGIN_URI"));
    }

    public String getRedisHost() {
        return System.getenv("REDIS_HOST");
    }

    public int getRedisPort() {
        return Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
    }

    public boolean getUseRedisTLS() {
        return Boolean.parseBoolean(System.getenv().getOrDefault("REDIS_TLS", "false"));
    }

    public String getRedisPassword() {
        return System.getenv("REDIS_PASSWORD");
    }

    public String getNotifyApiKey() {
        return System.getenv("NOTIFY_API_KEY");
    }

    public String getNotificationTemplateId(NotificationType notificationType) {
        switch(notificationType) {
            case VERIFY_EMAIL:
                return System.getenv("VERIFY_EMAIL_TEMPLATE_ID");
            default:
                throw new RuntimeException("NotificationType template ID does not exist");
        }
    }

    public String getEmailQueueUri() { return System.getenv("SQS_EMAIL"); }

    public String getAwsRegion() { return System.getenv("AWS_REGION"); }

    public Optional<String> getSqsEndpointUri() { return Optional.of(System.getenv("SQS_ENDPOINT")); }

}
