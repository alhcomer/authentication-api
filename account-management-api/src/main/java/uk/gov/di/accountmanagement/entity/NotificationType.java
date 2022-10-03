package uk.gov.di.accountmanagement.entity;

import uk.gov.di.authentication.shared.entity.TemplateAware;
import uk.gov.di.authentication.shared.helpers.LocaleHelper.SupportedLanguage;
import uk.gov.di.authentication.shared.services.ConfigurationService;

import java.util.EnumMap;
import java.util.Map;

public enum NotificationType implements TemplateAware {
    VERIFY_EMAIL(
            "VERIFY_EMAIL_TEMPLATE_ID",
            new EnumMap<>(Map.of(SupportedLanguage.CY, "VERIFY_EMAIL_TEMPLATE_ID_CY"))),
    EMAIL_UPDATED(
            "EMAIL_UPDATED_TEMPLATE_ID",
            new EnumMap<>(Map.of(SupportedLanguage.CY, "EMAIL_UPDATED_TEMPLATE_ID_CY"))),
    DELETE_ACCOUNT(
            "DELETE_ACCOUNT_TEMPLATE_ID",
            new EnumMap<>(Map.of(SupportedLanguage.CY, "DELETE_ACCOUNT_TEMPLATE_ID_CY"))),
    PHONE_NUMBER_UPDATED(
            "PHONE_NUMBER_UPDATED_TEMPLATE_ID",
            new EnumMap<>(Map.of(SupportedLanguage.CY, "PHONE_NUMBER_UPDATED_TEMPLATE_ID_CY"))),
    VERIFY_PHONE_NUMBER(
            "VERIFY_PHONE_NUMBER_TEMPLATE_ID",
            new EnumMap<>(Map.of(SupportedLanguage.CY, "VERIFY_PHONE_NUMBER_TEMPLATE_ID_CY"))),
    PASSWORD_UPDATED(
            "PASSWORD_UPDATED_TEMPLATE_ID",
            new EnumMap<>(Map.of(SupportedLanguage.CY, "PASSWORD_UPDATED_TEMPLATE_ID_CY")));

    private final String templateName;

    private EnumMap<SupportedLanguage, String> languageSpecificTemplates =
            new EnumMap<>(SupportedLanguage.class);

    NotificationType(String templateName) {
        this.templateName = templateName;
    }

    NotificationType(
            String templateName, EnumMap<SupportedLanguage, String> languageSpecificTemplates) {
        this(templateName);
        this.languageSpecificTemplates = languageSpecificTemplates;
    }

    @Override
    public String getTemplateId(
            SupportedLanguage language, ConfigurationService configurationService) {
        String templateId = configurationService.getNotifyTemplateId(getTemplateName(language));
        if (!configurationService.isNotifyTemplatePerLanguage()
                || templateId == null
                || templateId.length() == 0) {
            return configurationService.getNotifyTemplateId(templateName);
        } else {
            return templateId;
        }
    }

    String getTemplateName(SupportedLanguage language) {
        return languageSpecificTemplates.getOrDefault(language, templateName);
    }
}
