package uk.gov.di.authentication.frontendapi.entity;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import uk.gov.di.authentication.shared.entity.MFAMethodType;
import uk.gov.di.authentication.shared.validation.Required;

public class VerifyMfaCodeRequest {

    public VerifyMfaCodeRequest() {}

    public VerifyMfaCodeRequest(MFAMethodType mfaMethodType, String code, boolean isRegistration) {
        this(mfaMethodType, code, isRegistration, null);
    }

    public VerifyMfaCodeRequest(
            MFAMethodType mfaMethodType,
            String code,
            boolean isRegistration,
            String profileInformation) {
        this.mfaMethodType = mfaMethodType;
        this.code = code;
        this.isRegistration = isRegistration;
        this.profileInformation = profileInformation;
    }

    @SerializedName("mfaMethodType")
    @Expose
    @Required
    private MFAMethodType mfaMethodType;

    @SerializedName("code")
    @Expose
    @Required
    private String code;

    @SerializedName("isRegistration")
    @Expose
    @Required
    private boolean isRegistration;

    @SerializedName("profileInformation")
    @Expose
    private String profileInformation;

    public MFAMethodType getMfaMethodType() {
        return mfaMethodType;
    }

    public String getCode() {
        return code;
    }

    public boolean isRegistration() {
        return isRegistration;
    }

    public String getProfileInformation() {
        return profileInformation;
    }
}
