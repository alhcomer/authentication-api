package uk.gov.di.authentication.shared.entity;

import com.google.gson.annotations.Expose;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Session {

    private static final Logger LOG = LogManager.getLogger(Session.class);

    public enum AccountState {
        NEW,
        EXISTING,
        EXISTING_DOC_APP_JOURNEY,
        UNKNOWN
    }

    @Expose private String sessionId;

    @Expose private List<String> clientSessions;

    @Expose private String emailAddress;

    @Expose private int retryCount;

    @Expose private int passwordResetCount;

    @Expose private Map<CodeRequestType, Integer> codeRequestCountMap;

    @Expose private CredentialTrustLevel currentCredentialStrength;

    @Expose private AccountState isNewAccount;

    @Expose private boolean authenticated;

    @Expose private int processingIdentityAttempts;

    @Expose private MFAMethodType verifiedMfaMethodType;

    @Expose private String internalCommonSubjectIdentifier;

    public Session(String sessionId) {
        this.sessionId = sessionId;
        this.clientSessions = new ArrayList<>();
        this.isNewAccount = AccountState.UNKNOWN;
        this.processingIdentityAttempts = 0;
        this.codeRequestCountMap = new HashMap<>();
        initializeCodeRequestMap();
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<String> getClientSessions() {
        return clientSessions;
    }

    public Session addClientSession(String clientSessionId) {
        this.clientSessions.add(clientSessionId);
        return this;
    }

    public boolean validateSession(String emailAddress) {
        return this.emailAddress.equals(emailAddress);
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public Session setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
        return this;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getPasswordResetCount() {
        return passwordResetCount;
    }

    public Session incrementPasswordResetCount() {
        this.passwordResetCount = passwordResetCount + 1;
        return this;
    }

    public Session resetPasswordResetCount() {
        this.passwordResetCount = 0;
        return this;
    }

    public int getCodeRequestCount(NotificationType notificationType, JourneyType journeyType) {
        CodeRequestType requestType =
                CodeRequestType.getCodeRequestType(notificationType, journeyType);
        return getCodeRequestCount(requestType);
    }

    public int getCodeRequestCount(CodeRequestType requestType) {
        LOG.info("CodeRequest count: {}", codeRequestCountMap);
        return codeRequestCountMap.getOrDefault(requestType, 0);
    }

    public Session incrementCodeRequestCount(
            NotificationType notificationType, JourneyType journeyType) {
        CodeRequestType requestType =
                CodeRequestType.getCodeRequestType(notificationType, journeyType);
        int currentCount = getCodeRequestCount(requestType);
        codeRequestCountMap.put(requestType, currentCount + 1);
        LOG.info("CodeRequest count incremented: {}", codeRequestCountMap);

        return this;
    }

    public Session resetCodeRequestCount(
            NotificationType notificationType, JourneyType journeyType) {
        CodeRequestType requestType =
                CodeRequestType.getCodeRequestType(notificationType, journeyType);
        codeRequestCountMap.put(requestType, 0);
        LOG.info("CodeRequest count reset: {}", codeRequestCountMap);
        return this;
    }

    public CredentialTrustLevel getCurrentCredentialStrength() {
        return currentCredentialStrength;
    }

    public Session setCurrentCredentialStrength(CredentialTrustLevel currentCredentialStrength) {
        this.currentCredentialStrength = currentCredentialStrength;
        return this;
    }

    public AccountState isNewAccount() {
        return isNewAccount;
    }

    public Session setNewAccount(AccountState isNewAccount) {
        this.isNewAccount = isNewAccount;
        return this;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public Session setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        return this;
    }

    public int getProcessingIdentityAttempts() {
        return processingIdentityAttempts;
    }

    public void resetProcessingIdentityAttempts() {
        this.processingIdentityAttempts = 0;
    }

    public int incrementProcessingIdentityAttempts() {
        this.processingIdentityAttempts += 1;
        return processingIdentityAttempts;
    }

    public MFAMethodType getVerifiedMfaMethodType() {
        return verifiedMfaMethodType;
    }

    public Session setVerifiedMfaMethodType(MFAMethodType verifiedMfaMethodType) {
        this.verifiedMfaMethodType = verifiedMfaMethodType;
        return this;
    }

    public String getInternalCommonSubjectIdentifier() {
        return internalCommonSubjectIdentifier;
    }

    public Session setInternalCommonSubjectIdentifier(String internalCommonSubjectIdentifier) {
        this.internalCommonSubjectIdentifier = internalCommonSubjectIdentifier;
        return this;
    }

    private void initializeCodeRequestMap() {
        for (CodeRequestType requestType : CodeRequestType.values()) {
            codeRequestCountMap.put(requestType, 0);
        }
    }
}
