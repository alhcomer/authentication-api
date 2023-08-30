package uk.gov.di.authentication.utils.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import uk.gov.di.authentication.shared.entity.BulkEmailStatus;
import uk.gov.di.authentication.shared.entity.BulkEmailUser;
import uk.gov.di.authentication.shared.entity.UserProfile;
import uk.gov.di.authentication.shared.helpers.ClientSubjectHelper;
import uk.gov.di.authentication.shared.helpers.LocaleHelper;
import uk.gov.di.authentication.shared.helpers.SaltHelper;
import uk.gov.di.authentication.shared.services.AuditService;
import uk.gov.di.authentication.shared.services.BulkEmailUsersService;
import uk.gov.di.authentication.shared.services.CloudwatchMetricsService;
import uk.gov.di.authentication.shared.services.ConfigurationService;
import uk.gov.di.authentication.shared.services.DynamoService;
import uk.gov.di.authentication.shared.services.NotificationService;
import uk.gov.di.authentication.utils.domain.UtilsAuditableEvent;
import uk.gov.service.notify.NotificationClientException;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.di.authentication.shared.entity.NotificationType.TERMS_AND_CONDITIONS_BULK_EMAIL;
import static uk.gov.di.authentication.shared.services.AuditService.MetadataPair.pair;

class BulkUserEmailSenderScheduledEventHandlerTest {

    private BulkUserEmailSenderScheduledEventHandler bulkUserEmailSenderScheduledEventHandler;

    private final Context mockContext = mock(Context.class);

    private final BulkEmailUsersService bulkEmailUsersService = mock(BulkEmailUsersService.class);

    private final DynamoService dynamoService = mock(DynamoService.class);

    private final NotificationService notificationService = mock(NotificationService.class);

    private final ConfigurationService configurationService = mock(ConfigurationService.class);

    private final AuditService auditService = mock(AuditService.class);

    private final CloudwatchMetricsService cloudwatchMetricsService =
            mock(CloudwatchMetricsService.class);

    private final DescribeTableResponse describeTableResponse = mock(DescribeTableResponse.class);

    private final ScheduledEvent scheduledEvent = mock(ScheduledEvent.class);

    private static final String SUBJECT_ID = "subject-id";
    private static final String EMAIL = "user@account.gov.uk";
    private static final String INTERNAL_SECTOR_URI = "https://test.account.gov.uk";
    private static final byte[] SALT = SaltHelper.generateNewSalt();
    private static final ByteBuffer SALT_BYTE_BUFFER = ByteBuffer.wrap(SALT);
    private final String expectedCommonSubject =
            ClientSubjectHelper.calculatePairwiseIdentifier(
                    SUBJECT_ID, "test.account.gov.uk", SALT);

    private final String[] TEST_EMAILS = {
        "user.1@account.gov.uk",
        "user.2@account.gov.uk",
        "user.3@account.gov.uk",
        "user.4@account.gov.uk",
        "user.5@account.gov.uk"
    };
    private final String[] TEST_SUBJECT_IDS = {
        "subject-id-1", "subject-id-2", "subject-id-3", "subject-id-4", "subject-id-5",
    };

    @BeforeEach
    void setUp() {
        bulkUserEmailSenderScheduledEventHandler =
                new BulkUserEmailSenderScheduledEventHandler(
                        bulkEmailUsersService,
                        dynamoService,
                        configurationService,
                        notificationService,
                        cloudwatchMetricsService,
                        auditService);
        when(bulkEmailUsersService.describeTable()).thenReturn(describeTableResponse);
        when(describeTableResponse.table())
                .thenReturn(TableDescription.builder().itemCount(1L).build());
        when(configurationService.getEnvironment()).thenReturn("unit-test");
        when(configurationService.getInternalSectorUri()).thenReturn(INTERNAL_SECTOR_URI);
    }

    @Test
    void
            shouldSendSingleBatchOfSingleEmailAndNotSubmitAuditEventForUserWithoutSaltThenUpdateStatusToEmailSent()
                    throws NotificationClientException {
        when(configurationService.getBulkUserEmailBatchQueryLimit()).thenReturn(1);
        when(configurationService.getBulkUserEmailMaxBatchCount()).thenReturn(1);
        when(configurationService.getBulkUserEmailBatchPauseDuration()).thenReturn(1L);
        when(configurationService.isBulkUserEmailEmailSendingEnabled()).thenReturn(true);
        when(bulkEmailUsersService.getNSubjectIdsByStatus(1, BulkEmailStatus.PENDING))
                .thenReturn(List.of(SUBJECT_ID));
        when(dynamoService.getOrGenerateSalt(any(UserProfile.class))).thenReturn(SALT);
        when(dynamoService.getOptionalUserProfileFromSubject(SUBJECT_ID))
                .thenReturn(
                        Optional.of(new UserProfile().withEmail(EMAIL).withSubjectID(SUBJECT_ID)));
        when(bulkEmailUsersService.updateUserStatus(SUBJECT_ID, BulkEmailStatus.EMAIL_SENT))
                .thenReturn(
                        Optional.of(
                                new BulkEmailUser()
                                        .withBulkEmailStatus(BulkEmailStatus.EMAIL_SENT)
                                        .withSubjectID(SUBJECT_ID)));

        bulkUserEmailSenderScheduledEventHandler.handleRequest(scheduledEvent, mockContext);

        verify(notificationService, times(1))
                .sendEmail(
                        EMAIL,
                        Map.of(),
                        TERMS_AND_CONDITIONS_BULK_EMAIL,
                        LocaleHelper.SupportedLanguage.EN);
        verify(bulkEmailUsersService, times(1))
                .updateUserStatus(SUBJECT_ID, BulkEmailStatus.EMAIL_SENT);
        verify(auditService)
                .submitAuditEvent(
                        UtilsAuditableEvent.BULK_EMAIL_SENT,
                        AuditService.UNKNOWN,
                        AuditService.UNKNOWN,
                        AuditService.UNKNOWN,
                        AuditService.UNKNOWN,
                        EMAIL,
                        AuditService.UNKNOWN,
                        AuditService.UNKNOWN,
                        AuditService.UNKNOWN,
                        pair("internalSubjectId", SUBJECT_ID),
                        pair("bulk-email-type", "VC_EXPIRY_BULK_EMAIL"));
    }

    @Test
    void shouldNotSendEmailOrAuditEventWhenSendEmailFlagNotEnabledAndUpdateStatusToEmailSent()
            throws NotificationClientException {
        when(configurationService.getBulkUserEmailBatchQueryLimit()).thenReturn(1);
        when(configurationService.getBulkUserEmailMaxBatchCount()).thenReturn(1);
        when(configurationService.getBulkUserEmailBatchPauseDuration()).thenReturn(1L);
        when(configurationService.isBulkUserEmailEmailSendingEnabled()).thenReturn(false);
        when(bulkEmailUsersService.getNSubjectIdsByStatus(1, BulkEmailStatus.PENDING))
                .thenReturn(List.of(SUBJECT_ID));
        when(dynamoService.getOrGenerateSalt(any(UserProfile.class))).thenReturn(SALT);
        when(dynamoService.getOptionalUserProfileFromSubject(SUBJECT_ID))
                .thenReturn(
                        Optional.of(
                                new UserProfile()
                                        .withEmail(EMAIL)
                                        .withSubjectID(SUBJECT_ID)
                                        .withSalt(SALT_BYTE_BUFFER)));
        when(bulkEmailUsersService.updateUserStatus(SUBJECT_ID, BulkEmailStatus.EMAIL_SENT))
                .thenReturn(
                        Optional.of(
                                new BulkEmailUser()
                                        .withBulkEmailStatus(BulkEmailStatus.EMAIL_SENT)
                                        .withSubjectID(SUBJECT_ID)));

        bulkUserEmailSenderScheduledEventHandler.handleRequest(scheduledEvent, mockContext);

        verify(notificationService, times(0))
                .sendEmail(
                        EMAIL,
                        Map.of(),
                        TERMS_AND_CONDITIONS_BULK_EMAIL,
                        LocaleHelper.SupportedLanguage.EN);
        verify(bulkEmailUsersService, times(1))
                .updateUserStatus(SUBJECT_ID, BulkEmailStatus.EMAIL_SENT);
        verify(auditService, never())
                .submitAuditEvent(
                        UtilsAuditableEvent.BULK_EMAIL_SENT,
                        AuditService.UNKNOWN,
                        AuditService.UNKNOWN,
                        AuditService.UNKNOWN,
                        expectedCommonSubject,
                        EMAIL,
                        AuditService.UNKNOWN,
                        AuditService.UNKNOWN,
                        AuditService.UNKNOWN,
                        pair("internalSubjectId", SUBJECT_ID),
                        pair("bulk-email-type", "VC_EXPIRY_BULK_EMAIL"));
    }

    @Test
    void shouldSendSingleBatchOfEmailsAndSendAuditEventsThenUpdateStatusToEmailSent()
            throws NotificationClientException {
        when(configurationService.getBulkUserEmailBatchQueryLimit()).thenReturn(5);
        when(configurationService.getBulkUserEmailMaxBatchCount()).thenReturn(1);
        when(configurationService.isBulkUserEmailEmailSendingEnabled()).thenReturn(true);
        when(dynamoService.getOrGenerateSalt(any(UserProfile.class))).thenReturn(SALT);
        when(bulkEmailUsersService.getNSubjectIdsByStatus(5, BulkEmailStatus.PENDING))
                .thenReturn(Arrays.asList(TEST_SUBJECT_IDS));
        for (int i = 0; i < TEST_SUBJECT_IDS.length; i++) {
            when(dynamoService.getOptionalUserProfileFromSubject(TEST_SUBJECT_IDS[i]))
                    .thenReturn(
                            Optional.of(
                                    new UserProfile()
                                            .withEmail(TEST_EMAILS[i])
                                            .withSubjectID(TEST_SUBJECT_IDS[i])
                                            .withSalt(SALT_BYTE_BUFFER)));
            when(bulkEmailUsersService.updateUserStatus(
                            TEST_SUBJECT_IDS[i], BulkEmailStatus.EMAIL_SENT))
                    .thenReturn(
                            Optional.of(
                                    new BulkEmailUser()
                                            .withBulkEmailStatus(BulkEmailStatus.EMAIL_SENT)
                                            .withSubjectID(TEST_SUBJECT_IDS[i])));
        }

        bulkUserEmailSenderScheduledEventHandler.handleRequest(scheduledEvent, mockContext);

        for (int j = 0; j < TEST_EMAILS.length; j++) {
            var expectedInternalPairwiseId =
                    ClientSubjectHelper.calculatePairwiseIdentifier(
                            TEST_SUBJECT_IDS[j], "test.account.gov.uk", SALT);
            verify(notificationService, times(1))
                    .sendEmail(
                            TEST_EMAILS[j],
                            Map.of(),
                            TERMS_AND_CONDITIONS_BULK_EMAIL,
                            LocaleHelper.SupportedLanguage.EN);
            verify(bulkEmailUsersService, times(1))
                    .updateUserStatus(TEST_SUBJECT_IDS[j], BulkEmailStatus.EMAIL_SENT);
            verify(auditService)
                    .submitAuditEvent(
                            UtilsAuditableEvent.BULK_EMAIL_SENT,
                            AuditService.UNKNOWN,
                            AuditService.UNKNOWN,
                            AuditService.UNKNOWN,
                            expectedInternalPairwiseId,
                            TEST_EMAILS[j],
                            AuditService.UNKNOWN,
                            AuditService.UNKNOWN,
                            AuditService.UNKNOWN,
                            pair("internalSubjectId", TEST_SUBJECT_IDS[j]),
                            pair("bulk-email-type", "VC_EXPIRY_BULK_EMAIL"));
        }
    }

    @Test
    void shouldNotSendSingleEmailAndUpdateStatusToAccountNotFoundWhenNoUserProfileForSubjectId()
            throws NotificationClientException {
        when(configurationService.getBulkUserEmailBatchQueryLimit()).thenReturn(1);
        when(configurationService.getBulkUserEmailMaxBatchCount()).thenReturn(1);
        when(configurationService.isBulkUserEmailEmailSendingEnabled()).thenReturn(true);
        when(bulkEmailUsersService.getNSubjectIdsByStatus(1, BulkEmailStatus.PENDING))
                .thenReturn(List.of(SUBJECT_ID));
        when(dynamoService.getOptionalUserProfileFromSubject(SUBJECT_ID))
                .thenReturn(Optional.empty());
        when(bulkEmailUsersService.updateUserStatus(SUBJECT_ID, BulkEmailStatus.ACCOUNT_NOT_FOUND))
                .thenReturn(
                        Optional.of(
                                new BulkEmailUser()
                                        .withBulkEmailStatus(BulkEmailStatus.ACCOUNT_NOT_FOUND)
                                        .withSubjectID(SUBJECT_ID)));

        bulkUserEmailSenderScheduledEventHandler.handleRequest(scheduledEvent, mockContext);

        verify(notificationService, times(0))
                .sendEmail(
                        EMAIL,
                        Map.of(),
                        TERMS_AND_CONDITIONS_BULK_EMAIL,
                        LocaleHelper.SupportedLanguage.EN);
        verify(bulkEmailUsersService, times(1))
                .updateUserStatus(SUBJECT_ID, BulkEmailStatus.ACCOUNT_NOT_FOUND);
        verifyNoInteractions(auditService);
    }

    @Test
    void shouldNotSendSingleEmailAndUpdateStatusToErrorWhenNotifySendEmailFails()
            throws NotificationClientException {
        when(configurationService.getBulkUserEmailBatchQueryLimit()).thenReturn(1);
        when(configurationService.getBulkUserEmailMaxBatchCount()).thenReturn(1);
        when(configurationService.isBulkUserEmailEmailSendingEnabled()).thenReturn(true);
        when(bulkEmailUsersService.getNSubjectIdsByStatus(1, BulkEmailStatus.PENDING))
                .thenReturn(List.of(SUBJECT_ID));
        when(dynamoService.getOptionalUserProfileFromSubject(SUBJECT_ID))
                .thenReturn(Optional.of(new UserProfile().withEmail(EMAIL)));
        doThrow(NotificationClientException.class)
                .when(notificationService)
                .sendEmail(
                        EMAIL,
                        Map.of(),
                        TERMS_AND_CONDITIONS_BULK_EMAIL,
                        LocaleHelper.SupportedLanguage.EN);
        when(bulkEmailUsersService.updateUserStatus(
                        SUBJECT_ID, BulkEmailStatus.ERROR_SENDING_EMAIL))
                .thenReturn(
                        Optional.of(
                                new BulkEmailUser()
                                        .withBulkEmailStatus(BulkEmailStatus.ERROR_SENDING_EMAIL)
                                        .withSubjectID(SUBJECT_ID)));

        bulkUserEmailSenderScheduledEventHandler.handleRequest(scheduledEvent, mockContext);

        verify(notificationService, times(1))
                .sendEmail(
                        EMAIL,
                        Map.of(),
                        TERMS_AND_CONDITIONS_BULK_EMAIL,
                        LocaleHelper.SupportedLanguage.EN);
        verify(bulkEmailUsersService, times(1))
                .updateUserStatus(SUBJECT_ID, BulkEmailStatus.ERROR_SENDING_EMAIL);
        verifyNoInteractions(auditService);
    }
}
