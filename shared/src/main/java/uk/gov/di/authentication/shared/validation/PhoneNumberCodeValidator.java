package uk.gov.di.authentication.shared.validation;

import uk.gov.di.authentication.entity.CodeRequest;
import uk.gov.di.authentication.shared.entity.ErrorResponse;
import uk.gov.di.authentication.shared.entity.JourneyType;
import uk.gov.di.authentication.shared.entity.NotificationType;
import uk.gov.di.authentication.shared.exceptions.ClientNotFoundException;
import uk.gov.di.authentication.shared.helpers.ValidationHelper;
import uk.gov.di.authentication.shared.services.CodeStorageService;
import uk.gov.di.authentication.shared.services.ConfigurationService;
import uk.gov.di.authentication.shared.state.UserContext;

import java.util.Optional;

import static uk.gov.di.authentication.shared.helpers.TestClientHelper.isTestClientWithAllowedEmail;

public class PhoneNumberCodeValidator extends MfaCodeValidator {

    private final ConfigurationService configurationService;
    private final UserContext userContext;
    private final JourneyType journeyType;

    PhoneNumberCodeValidator(
            CodeStorageService codeStorageService,
            UserContext userContext,
            ConfigurationService configurationService,
            JourneyType journeyType) {
        super(
                userContext.getSession().getEmailAddress(),
                codeStorageService,
                configurationService.getCodeMaxRetries());
        this.userContext = userContext;
        this.configurationService = configurationService;
        this.journeyType = journeyType;
    }

    @Override
    public Optional<ErrorResponse> validateCode(CodeRequest codeRequest) {
        var isRegistration = journeyType.getValue().equals(JourneyType.REGISTRATION.getValue());
        if (!isRegistration) {
            LOG.error("Sign In Phone number codes are not supported");
            throw new RuntimeException("Sign In Phone number codes are not supported");
        }
        var notificationType =
                isRegistration ? NotificationType.VERIFY_PHONE_NUMBER : NotificationType.MFA_SMS;
        if (isCodeBlockedForSession()) {
            LOG.info("Code blocked for session");
            return Optional.of(ErrorResponse.ERROR_1034);
        }
        boolean isTestClient;
        try {
            isTestClient = isTestClientWithAllowedEmail(userContext, configurationService);
        } catch (ClientNotFoundException e) {
            LOG.error("No client found", e);
            throw new RuntimeException(e);
        }
        var storedCode =
                isTestClient
                        ? configurationService.getTestClientVerifyPhoneNumberOTP()
                        : codeStorageService.getOtpCode(emailAddress, notificationType);

        return ValidationHelper.validateVerificationCode(
                notificationType,
                storedCode,
                codeRequest.getCode(),
                codeStorageService,
                emailAddress,
                configurationService.getCodeMaxRetries());
    }
}