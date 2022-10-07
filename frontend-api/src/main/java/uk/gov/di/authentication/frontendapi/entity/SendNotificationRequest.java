package uk.gov.di.authentication.frontendapi.entity;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import uk.gov.di.authentication.shared.entity.BaseFrontendRequest;
import uk.gov.di.authentication.shared.entity.NotificationType;
import uk.gov.di.authentication.shared.validation.Required;

public class SendNotificationRequest extends BaseFrontendRequest {

    @SerializedName("notificationType")
    @Expose
    @Required
    private NotificationType notificationType;

    @SerializedName("phoneNumber")
    @Expose
    private String phoneNumber;

    @SerializedName("requestNewCode")
    @Expose
    private Boolean requestNewCode;

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public Boolean isRequestNewCode() {
        return requestNewCode;
    }

    @Override
    public String toString() {
        return "SendNotificationRequest{"
                + "notificationType="
                + notificationType
                + ", phoneNumber='"
                + phoneNumber
                + '\''
                + ", email='"
                + email
                + '\''
                + ", requestNewCode='"
                + requestNewCode
                + '\''
                + '}';
    }
}
