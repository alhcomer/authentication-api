package uk.gov.di.authentication.shared.state;

import uk.gov.di.authentication.shared.entity.ClientRegistry;
import uk.gov.di.authentication.shared.entity.ClientSession;
import uk.gov.di.authentication.shared.entity.Session;
import uk.gov.di.authentication.shared.entity.UserCredentials;
import uk.gov.di.authentication.shared.entity.UserProfile;

import java.util.Optional;

public class UserContext {
    private final Session session;
    private final Optional<UserProfile> userProfile;

    private final Optional<UserCredentials> userCredentials;
    private final boolean userAuthenticated;
    private final Optional<ClientRegistry> client;
    private final ClientSession clientSession;

    protected UserContext(
            Session session,
            Optional<UserProfile> userProfile,
            Optional<UserCredentials> userCredentials,
            boolean userAuthenticated,
            Optional<ClientRegistry> client,
            ClientSession clientSession) {
        this.session = session;
        this.userProfile = userProfile;
        this.userCredentials = userCredentials;
        this.userAuthenticated = userAuthenticated;
        this.client = client;
        this.clientSession = clientSession;
    }

    public Session getSession() {
        return session;
    }

    public Optional<UserProfile> getUserProfile() {
        return userProfile;
    }

    public Optional<UserCredentials> getUserCredentials() {
        return userCredentials;
    }

    public boolean isUserAuthenticated() {
        return userAuthenticated;
    }

    public Optional<ClientRegistry> getClient() {
        return client;
    }

    public ClientSession getClientSession() {
        return clientSession;
    }

    public static Builder builder(Session session) {
        return new Builder(session);
    }

    public static class Builder {
        private Session session;
        private Optional<UserProfile> userProfile = Optional.empty();
        private Optional<UserCredentials> userCredentials = Optional.empty();
        private boolean userAuthenticated = false;
        private Optional<ClientRegistry> client = Optional.empty();
        private ClientSession clientSession = null;

        protected Builder(Session session) {
            this.session = session;
        }

        public Builder withUserProfile(UserProfile userProfile) {
            return withUserProfile(Optional.of(userProfile));
        }

        public Builder withUserProfile(Optional<UserProfile> userProfile) {
            this.userProfile = userProfile;
            return this;
        }

        public Builder withUserCredentials(Optional<UserCredentials> userCredentials) {
            this.userCredentials = userCredentials;
            return this;
        }

        public Builder withUserAuthenticated(boolean userAuthenticated) {
            this.userAuthenticated = userAuthenticated;
            return this;
        }

        public Builder withClient(ClientRegistry client) {
            return withClient(Optional.of(client));
        }

        public Builder withClient(Optional<ClientRegistry> client) {
            this.client = client;
            return this;
        }

        public Builder withClientSession(ClientSession clientSession) {
            this.clientSession = clientSession;
            return this;
        }

        public UserContext build() {
            return new UserContext(
                    session,
                    userProfile,
                    userCredentials,
                    userAuthenticated,
                    client,
                    clientSession);
        }
    }
}
