package uk.gov.di.authentication.shared.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.nio.ByteBuffer;
import java.util.List;

@DynamoDbBean
public class UserProfile {

    public static final String ATTRIBUTE_EMAIL = "Email";
    public static final String ATTRIBUTE_SUBJECT_ID = "SubjectID";
    public static final String ATTRIBUTE_EMAIL_VERIFIED = "EmailVerified";
    public static final String ATTRIBUTE_PHONE_NUMBER = "PhoneNumber";
    public static final String ATTRIBUTE_PHONE_NUMBER_VERIFIED = "PhoneNumberVerified";
    public static final String ATTRIBUTE_CREATED = "Created";
    public static final String ATTRIBUTE_UPDATED = "Updated";
    public static final String ATTRIBUTE_TERMS_AND_CONDITIONS = "termsAndConditions";
    public static final String ATTRIBUTE_CLIENT_CONSENT = "ClientConsent";
    public static final String ATTRIBUTE_PUBLIC_SUBJECT_ID = "PublicSubjectID";
    public static final String ATTRIBUTE_LEGACY_SUBJECT_ID = "LegacySubjectID";
    public static final String ATTRIBUTE_SALT = "salt";
    public static final String ATTRIBUTE_ACCOUNT_VERIFIED = "accountVerified";

    private String email;
    private String subjectID;
    private boolean emailVerified;
    private String phoneNumber;
    private List<ClientConsent> clientConsent;
    private boolean phoneNumberVerified;
    private String created;
    private String updated;
    private TermsAndConditions termsAndConditions;
    private String publicSubjectID;
    private String legacySubjectID;
    private ByteBuffer salt;
    private int accountVerified;

    public UserProfile() {}

    @DynamoDbPartitionKey
    @DynamoDbAttribute(ATTRIBUTE_EMAIL)
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserProfile withEmail(String email) {
        this.email = email;
        return this;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"SubjectIDIndex"})
    @DynamoDbAttribute(ATTRIBUTE_SUBJECT_ID)
    public String getSubjectID() {
        return subjectID;
    }

    public void setSubjectID(String subjectID) {
        this.subjectID = subjectID;
    }

    public UserProfile withSubjectID(String subjectID) {
        this.subjectID = subjectID;
        return this;
    }

    @DynamoDbAttribute(ATTRIBUTE_EMAIL_VERIFIED)
    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public UserProfile withEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
        return this;
    }

    @DynamoDbAttribute(ATTRIBUTE_PHONE_NUMBER)
    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public UserProfile withPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        return this;
    }

    @DynamoDbAttribute(ATTRIBUTE_PHONE_NUMBER_VERIFIED)
    public boolean isPhoneNumberVerified() {
        return phoneNumberVerified;
    }

    public void setPhoneNumberVerified(boolean phoneNumberVerified) {
        this.phoneNumberVerified = phoneNumberVerified;
    }

    public UserProfile withPhoneNumberVerified(boolean phoneNumberVerified) {
        this.phoneNumberVerified = phoneNumberVerified;
        return this;
    }

    @DynamoDbAttribute(ATTRIBUTE_CREATED)
    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public UserProfile withCreated(String created) {
        this.created = created;
        return this;
    }

    @DynamoDbAttribute(ATTRIBUTE_UPDATED)
    public String getUpdated() {
        return updated;
    }

    public void setUpdated(String updated) {
        this.updated = updated;
    }

    public UserProfile withUpdated(String updated) {
        this.updated = updated;
        return this;
    }

    @DynamoDbAttribute(ATTRIBUTE_TERMS_AND_CONDITIONS)
    public TermsAndConditions getTermsAndConditions() {
        return termsAndConditions;
    }

    public void setTermsAndConditions(TermsAndConditions termsAndConditions) {
        this.termsAndConditions = termsAndConditions;
    }

    public UserProfile withTermsAndConditions(TermsAndConditions termsAndConditions) {
        this.termsAndConditions = termsAndConditions;
        return this;
    }

    @DynamoDbAttribute(ATTRIBUTE_CLIENT_CONSENT)
    public List<ClientConsent> getClientConsent() {
        return clientConsent;
    }

    public void setClientConsent(List<ClientConsent> clientConsent) {
        this.clientConsent = clientConsent;
    }

    public UserProfile withClientConsent(ClientConsent consent) {
        if (this.clientConsent == null) {
            this.clientConsent = List.of(consent);
        } else {
            this.clientConsent.removeIf(t -> t.getClientId().equals(consent.getClientId()));
            this.clientConsent.add(consent);
        }
        return this;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"PublicSubjectIDIndex"})
    @DynamoDbAttribute(ATTRIBUTE_PUBLIC_SUBJECT_ID)
    public String getPublicSubjectID() {
        return publicSubjectID;
    }

    public void setPublicSubjectID(String publicSubjectID) {
        this.publicSubjectID = publicSubjectID;
    }

    public UserProfile withPublicSubjectID(String publicSubjectID) {
        this.publicSubjectID = publicSubjectID;
        return this;
    }

    @DynamoDbAttribute(ATTRIBUTE_LEGACY_SUBJECT_ID)
    public String getLegacySubjectID() {
        return legacySubjectID;
    }

    public void setLegacySubjectID(String legacySubjectID) {
        this.legacySubjectID = legacySubjectID;
    }

    public UserProfile withLegacySubjectID(String legacySubjectID) {
        this.legacySubjectID = legacySubjectID;
        return this;
    }

    @DynamoDbAttribute(ATTRIBUTE_SALT)
    public ByteBuffer getSalt() {
        return salt;
    }

    public void setSalt(ByteBuffer salt) {
        this.salt = salt;
    }

    public void setSalt(byte[] salt) {
        this.salt = ByteBuffer.wrap(salt).asReadOnlyBuffer();
    }

    public UserProfile withSalt(ByteBuffer salt) {
        this.salt = salt;
        return this;
    }

    @DynamoDbAttribute(ATTRIBUTE_ACCOUNT_VERIFIED)
    public int getAccountVerified() {
        return accountVerified;
    }

    public void setAccountVerified(int accountVerified) {
        this.accountVerified = accountVerified;
    }

    public UserProfile withAccountVerified(int accountVerified) {
        this.accountVerified = accountVerified;
        return this;
    }
}
