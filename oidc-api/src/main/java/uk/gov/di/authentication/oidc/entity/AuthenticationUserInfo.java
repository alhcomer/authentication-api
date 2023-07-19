package uk.gov.di.authentication.oidc.entity;

import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
public class AuthenticationUserInfo {

    private String subjectID;
    private UserInfo userInfo;
    private long timeToExist;

    public AuthenticationUserInfo() {}

    @DynamoDbPartitionKey
    @DynamoDbAttribute("SubjectID")
    public String getSubjectID() {
        return subjectID;
    }

    public void setSubjectID(String subjectID) {
        this.subjectID = subjectID;
    }

    public AuthenticationUserInfo withSubjectID(String subjectID) {
        this.subjectID = subjectID;
        return this;
    }

    @DynamoDbAttribute("UserInfo")
    public UserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(UserInfo userInfo) {
        this.userInfo = userInfo;
    }

    public AuthenticationUserInfo withUserInfo(UserInfo userInfo) {
        this.userInfo = userInfo;
        return this;
    }

    @DynamoDbAttribute("TimeToExist")
    public long getTimeToExist() {
        return timeToExist;
    }

    public void setTimeToExist(long timeToExist) {
        this.timeToExist = timeToExist;
    }

    public AuthenticationUserInfo withTimeToExist(long timeToExist) {
        this.timeToExist = timeToExist;
        return this;
    }
}
