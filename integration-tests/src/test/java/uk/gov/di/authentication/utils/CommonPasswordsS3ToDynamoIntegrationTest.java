package uk.gov.di.authentication.utils;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import uk.gov.di.authentication.sharedtest.basetest.HandlerIntegrationTest;
import uk.gov.di.authentication.sharedtest.extensions.CommonPasswordsS3Extension;
import uk.gov.di.authentication.sharedtest.helper.S3TestEventHelper;
import uk.gov.di.authentication.utils.lambda.S3ToDynamoDbHandler;

import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static uk.gov.di.authentication.sharedtest.extensions.CommonPasswordsS3Extension.COMMON_PASSWORDS_BUCKET;
import static uk.gov.di.authentication.sharedtest.extensions.CommonPasswordsS3Extension.TEST_FILE_NAME;

class CommonPasswordsS3ToDynamoIntegrationTest extends HandlerIntegrationTest<S3Event, Void> {

    private static final String REGION =
            Optional.ofNullable(System.getenv().get("AWS_REGION")).orElse("eu-west-2");
    private static final String S3_ENDPOINT =
            Optional.ofNullable(System.getenv().get("LOCALSTACK_ENDPOINT"))
                    .orElse("http://localhost:45678");
    private static final S3Event testS3Event =
            S3TestEventHelper.generateS3TestEvent(
                    REGION, "ObjectCreated:Put", COMMON_PASSWORDS_BUCKET, TEST_FILE_NAME);

    @RegisterExtension
    protected static final CommonPasswordsS3Extension commonPasswordsS3 =
            new CommonPasswordsS3Extension();

    @BeforeEach
    void setup() {
        var mockS3Credentials = AwsBasicCredentials.create("access", "secret");

        var testS3Client =
                S3Client.builder()
                        .endpointOverride(URI.create(S3_ENDPOINT))
                        .region(Region.of(REGION))
                        .serviceConfiguration(
                                S3Configuration.builder().pathStyleAccessEnabled(true).build())
                        .credentialsProvider(StaticCredentialsProvider.create(mockS3Credentials))
                        .build();
        handler = new S3ToDynamoDbHandler(TEST_CONFIGURATION_SERVICE, testS3Client);
    }

    @Test
    void movedS3TextIntoDynamoWhenTriggered() throws Exception {
        handler.handleRequest(testS3Event, mock(Context.class));

        List<String> testPasswords = getTestFilePasswords();

        testPasswords.forEach(password -> assertTrue(commonPasswords.isCommonPassword(password)));
    }

    private List<String> getTestFilePasswords() throws Exception {
        URL testFileUrl =
                Thread.currentThread()
                        .getContextClassLoader()
                        .getResource("common_passwords_integration_test.txt");
        Path testFilePath = Paths.get(testFileUrl.toURI());

        String testFileContent = Files.readString(testFilePath);
        String[] testFileContentAsArray = testFileContent.split("\r?\n|\r");
        List<String> testFileContentAsArrayList =
                new ArrayList<>(Arrays.asList(testFileContentAsArray));
        testFileContentAsArrayList.removeAll(Collections.singleton(null));
        testFileContentAsArrayList.removeAll(Collections.singleton(""));
        return testFileContentAsArrayList;
    }
}
