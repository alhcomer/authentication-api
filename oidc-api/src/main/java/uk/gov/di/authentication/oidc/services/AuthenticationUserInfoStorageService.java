package uk.gov.di.authentication.oidc.services;

import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import uk.gov.di.authentication.oidc.entity.AuthenticationUserInfo;
import uk.gov.di.authentication.shared.helpers.NowHelper;
import uk.gov.di.authentication.shared.services.BaseDynamoService;
import uk.gov.di.authentication.shared.services.ConfigurationService;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

public class AuthenticationUserInfoStorageService
        extends BaseDynamoService<AuthenticationUserInfo> {

    private final long timeToExist;

    public AuthenticationUserInfoStorageService(ConfigurationService configurationService) {
        super(
                AuthenticationUserInfo.class,
                "authentication-callback-userinfo",
                configurationService);
        this.timeToExist = configurationService.getSessionExpiry();
    }

    public void addAuthenticationUserInfoData(String subjectID, UserInfo userInfo) {
        String userInfoJson = userInfo.toJSONString();
        var userInfoDbObject =
                new AuthenticationUserInfo()
                        .withSubjectID(subjectID)
                        .withUserInfo(userInfoJson)
                        .withTimeToExist(
                                NowHelper.nowPlus(timeToExist, ChronoUnit.SECONDS)
                                        .toInstant()
                                        .getEpochSecond());

        put(userInfoDbObject);
    }

    public Optional<AuthenticationUserInfo> getAuthenticationUserInfoData(String subjectID) {
        return get(subjectID)
                .filter(t -> t.getTimeToExist() > NowHelper.now().toInstant().getEpochSecond());
    }
}
