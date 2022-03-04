package uk.gov.di.authentication.shared.entity;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;

import java.nio.ByteBuffer;
import java.util.List;

public class UserProfile {

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

    public UserProfile() {}

    @DynamoDBHashKey(attributeName = "Email")
    public String getEmail() {
        return email;
    }

    public UserProfile setEmail(String email) {
        this.email = email;
        return this;
    }

    @DynamoDBIndexHashKey(globalSecondaryIndexName = "SubjectIDIndex", attributeName = "SubjectID")
    public String getSubjectID() {
        return subjectID;
    }

    public UserProfile setSubjectID(String subjectID) {
        this.subjectID = subjectID;
        return this;
    }

    @DynamoDBAttribute(attributeName = "EmailVerified")
    public boolean isEmailVerified() {
        return emailVerified;
    }

    public UserProfile setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
        return this;
    }

    @DynamoDBAttribute(attributeName = "PhoneNumber")
    public String getPhoneNumber() {
        return phoneNumber;
    }

    public UserProfile setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        return this;
    }

    @DynamoDBAttribute(attributeName = "PhoneNumberVerified")
    public boolean isPhoneNumberVerified() {
        return phoneNumberVerified;
    }

    public UserProfile setPhoneNumberVerified(boolean phoneNumberVerified) {
        this.phoneNumberVerified = phoneNumberVerified;
        return this;
    }

    @DynamoDBAttribute(attributeName = "Created")
    public String getCreated() {
        return created;
    }

    public UserProfile setCreated(String created) {
        this.created = created;
        return this;
    }

    @DynamoDBAttribute(attributeName = "Updated")
    public String getUpdated() {
        return updated;
    }

    public UserProfile setUpdated(String updated) {
        this.updated = updated;
        return this;
    }

    @DynamoDBAttribute(attributeName = "termsAndConditions")
    public TermsAndConditions getTermsAndConditions() {
        return termsAndConditions;
    }

    public UserProfile setTermsAndConditions(TermsAndConditions termsAndConditions) {
        this.termsAndConditions = termsAndConditions;
        return this;
    }

    @DynamoDBAttribute(attributeName = "ClientConsent")
    public List<ClientConsent> getClientConsent() {
        return clientConsent;
    }

    public UserProfile setClientConsent(List<ClientConsent> clientConsent) {
        this.clientConsent = clientConsent;
        return this;
    }

    public UserProfile setClientConsent(ClientConsent consent) {
        if (this.clientConsent == null) {
            this.clientConsent = List.of(consent);
        } else {
            this.clientConsent.removeIf(t -> t.getClientId().equals(consent.getClientId()));
            this.clientConsent.add(consent);
        }
        return this;
    }

    @DynamoDBIndexHashKey(
            globalSecondaryIndexName = "PublicSubjectIDIndex",
            attributeName = "PublicSubjectID")
    public String getPublicSubjectID() {
        return publicSubjectID;
    }

    public UserProfile setPublicSubjectID(String publicSubjectID) {
        this.publicSubjectID = publicSubjectID;
        return this;
    }

    @DynamoDBAttribute(attributeName = "LegacySubjectID")
    public String getLegacySubjectID() {
        return legacySubjectID;
    }

    public UserProfile setLegacySubjectID(String legacySubjectID) {
        this.legacySubjectID = legacySubjectID;
        return this;
    }

    @DynamoDBAttribute(attributeName = "salt")
    public ByteBuffer getSalt() {
        return salt;
    }

    public UserProfile setSalt(ByteBuffer salt) {
        this.salt = salt;
        return this;
    }

    public UserProfile setSalt(byte[] salt) {
        this.salt = ByteBuffer.wrap(salt);
        return this;
    }
}
