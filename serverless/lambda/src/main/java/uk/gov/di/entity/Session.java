package uk.gov.di.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;

import static uk.gov.di.entity.SessionState.NEW;

public class Session {

    @JsonProperty private String sessionId;

    @JsonProperty private AuthenticationRequest authenticationRequest;

    @JsonProperty private SessionState state;

    @JsonProperty private String emailAddress;

    public Session(String sessionId) {
        this.sessionId = sessionId;
        this.state = NEW;
    }

    @JsonCreator
    public Session(
            @JsonProperty("session_id") String sessionId,
            @JsonProperty("authentication_request") AuthenticationRequest authenticationRequest,
            @JsonProperty("state") SessionState state,
            @JsonProperty("email_address") String emailAddress) {
        this.sessionId = sessionId;
        this.authenticationRequest = authenticationRequest;
        this.state = state;
        this.emailAddress = emailAddress;
    }

    public String getSessionId() {
        return sessionId;
    }

    public AuthenticationRequest getAuthenticationRequest() {
        return authenticationRequest;
    }

    public Session setAuthenticationRequest(AuthenticationRequest authenticationRequest) {
        this.authenticationRequest = authenticationRequest;
        return this;
    }

    public boolean validateSession(String emailAddress) {
        return this.emailAddress.equals(emailAddress);
    }

    public SessionState getState() {
        return state;
    }

    public Session setState(SessionState state) {
        this.state = state;
        return this;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public Session setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
        return this;
    }
}
