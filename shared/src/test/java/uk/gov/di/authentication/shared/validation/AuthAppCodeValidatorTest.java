package uk.gov.di.authentication.shared.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.di.authentication.shared.entity.ErrorResponse;
import uk.gov.di.authentication.shared.entity.MFAMethod;
import uk.gov.di.authentication.shared.entity.MFAMethodType;
import uk.gov.di.authentication.shared.entity.Session;
import uk.gov.di.authentication.shared.entity.UserCredentials;
import uk.gov.di.authentication.shared.helpers.NowHelper;
import uk.gov.di.authentication.shared.services.CodeStorageService;
import uk.gov.di.authentication.shared.services.ConfigurationService;
import uk.gov.di.authentication.shared.services.DynamoService;
import uk.gov.di.authentication.sharedtest.helper.AuthAppStub;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.di.authentication.shared.services.CodeStorageService.CODE_BLOCKED_KEY_PREFIX;

class AuthAppCodeValidatorTest {
    AuthAppCodeValidator authAppCodeValidator;
    Session mockSession;
    CodeStorageService mockCodeStorageService;
    ConfigurationService mockConfigurationService;
    DynamoService mockDynamoService;

    private final int MAX_RETRIES = 5;

    @BeforeEach
    void setUp() {
        this.mockSession = mock(Session.class);
        this.mockCodeStorageService = mock(CodeStorageService.class);
        this.mockConfigurationService = mock(ConfigurationService.class);
        this.mockDynamoService = mock(DynamoService.class);
    }

    private static Stream<Arguments> validatorParams() {
        return Stream.of(Arguments.of(false, null), Arguments.of(true, "test-credential-value"));
    }

    @ParameterizedTest
    @MethodSource("validatorParams")
    void returnsNoErrorOnValidAuthCode(boolean isRegistration, String authAppSecret) {
        setUpValidAuthCode(isRegistration);
        var authAppStub = new AuthAppStub();
        String authCode =
                authAppStub.getAuthAppOneTimeCode(
                        "test-credential-value", NowHelper.now().getTime());

        assertEquals(Optional.empty(), authAppCodeValidator.validateCode(authCode, authAppSecret));
    }

    @ParameterizedTest
    @MethodSource("validatorParams")
    void returnsCorrectErrorWhenCodeBlockedForEmailAddress(
            boolean isRegistration, String authAppSecret) {
        setUpBlockedUser(isRegistration);

        assertEquals(
                Optional.of(ErrorResponse.ERROR_1042),
                authAppCodeValidator.validateCode("any-code", authAppSecret));
    }

    @ParameterizedTest
    @MethodSource("validatorParams")
    void returnsCorrectErrorWhenRetryLimitExceeded(boolean isRegistration, String authAppSecret) {
        setUpRetryLimitExceededUser(isRegistration);

        assertEquals(
                Optional.of(ErrorResponse.ERROR_1042),
                authAppCodeValidator.validateCode("any-code", authAppSecret));
    }

    @ParameterizedTest
    @MethodSource("validatorParams")
    void returnsCorrectErrorWhenNoAuthCodeIsFound(boolean isRegistration) {
        setUpNoAuthCodeForUser(isRegistration);

        assertEquals(
                Optional.of(ErrorResponse.ERROR_1043),
                authAppCodeValidator.validateCode("any-code", null));
    }

    @ParameterizedTest
    @MethodSource("validatorParams")
    void returnsCorrectErrorWhenAuthCodeIsInvalid(boolean isRegistration, String authAppSecret) {
        setUpValidAuthCode(isRegistration);

        assertEquals(
                Optional.of(ErrorResponse.ERROR_1043),
                authAppCodeValidator.validateCode("111111", authAppSecret));
        assertEquals(
                Optional.of(ErrorResponse.ERROR_1043),
                authAppCodeValidator.validateCode("", authAppSecret));
        assertEquals(
                Optional.of(ErrorResponse.ERROR_1043),
                authAppCodeValidator.validateCode("999999999999", authAppSecret));
    }

    private void setUpBlockedUser(boolean isRegistration) {
        when(mockCodeStorageService.isBlockedForEmail(
                        "blocked-email-address", CODE_BLOCKED_KEY_PREFIX))
                .thenReturn(true);

        this.authAppCodeValidator =
                new AuthAppCodeValidator(
                        "blocked-email-address",
                        mockCodeStorageService,
                        mockConfigurationService,
                        mockDynamoService,
                        MAX_RETRIES,
                        isRegistration);
    }

    private void setUpRetryLimitExceededUser(boolean isRegistration) {
        when(mockCodeStorageService.isBlockedForEmail("email-address", CODE_BLOCKED_KEY_PREFIX))
                .thenReturn(false);
        when(mockCodeStorageService.getIncorrectMfaCodeAttemptsCount(
                        "email-address", MFAMethodType.AUTH_APP))
                .thenReturn(MAX_RETRIES + 1);

        this.authAppCodeValidator =
                new AuthAppCodeValidator(
                        "email-address",
                        mockCodeStorageService,
                        mockConfigurationService,
                        mockDynamoService,
                        MAX_RETRIES,
                        isRegistration);
    }

    private void setUpNoAuthCodeForUser(boolean isRegistration) {
        when(mockCodeStorageService.isBlockedForEmail("email-address", CODE_BLOCKED_KEY_PREFIX))
                .thenReturn(false);
        when(mockDynamoService.getUserCredentialsFromEmail("email-address"))
                .thenReturn(mock(UserCredentials.class));

        this.authAppCodeValidator =
                new AuthAppCodeValidator(
                        "email-address",
                        mockCodeStorageService,
                        mockConfigurationService,
                        mockDynamoService,
                        MAX_RETRIES,
                        isRegistration);
    }

    private void setUpValidAuthCode(boolean isRegistration) {
        when(mockSession.getEmailAddress()).thenReturn("email-address");
        when(mockSession.getRetryCount()).thenReturn(0);
        when(mockCodeStorageService.isBlockedForEmail("email-address", CODE_BLOCKED_KEY_PREFIX))
                .thenReturn(false);
        when(mockConfigurationService.getAuthAppCodeAllowedWindows()).thenReturn(9);
        when(mockConfigurationService.getAuthAppCodeWindowLength()).thenReturn(30);

        UserCredentials mockUserCredentials = mock(UserCredentials.class);
        MFAMethod mockMfaMethod = mock(MFAMethod.class);
        when(mockMfaMethod.getMfaMethodType()).thenReturn(MFAMethodType.AUTH_APP.getValue());
        when(mockMfaMethod.getCredentialValue()).thenReturn("test-credential-value");
        when(mockMfaMethod.isEnabled()).thenReturn(true);
        List<MFAMethod> mockMfaMethodList = Collections.singletonList(mockMfaMethod);
        when(mockUserCredentials.getMfaMethods()).thenReturn(mockMfaMethodList);
        when(mockDynamoService.getUserCredentialsFromEmail("email-address"))
                .thenReturn(mockUserCredentials);

        this.authAppCodeValidator =
                new AuthAppCodeValidator(
                        "email-address",
                        mockCodeStorageService,
                        mockConfigurationService,
                        mockDynamoService,
                        MAX_RETRIES,
                        isRegistration);
    }
}
