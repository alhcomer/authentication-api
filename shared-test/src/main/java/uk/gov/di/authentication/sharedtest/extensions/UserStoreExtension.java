package uk.gov.di.authentication.sharedtest.extensions;

import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.Projection;
import com.nimbusds.oauth2.sdk.id.Subject;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import uk.gov.di.authentication.shared.entity.ClientConsent;
import uk.gov.di.authentication.shared.entity.MFAMethod;
import uk.gov.di.authentication.shared.entity.MFAMethodType;
import uk.gov.di.authentication.shared.entity.TermsAndConditions;
import uk.gov.di.authentication.shared.entity.UserProfile;
import uk.gov.di.authentication.shared.services.DynamoService;
import uk.gov.di.authentication.sharedtest.basetest.DynamoTestConfiguration;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static com.amazonaws.services.dynamodbv2.model.KeyType.HASH;
import static com.amazonaws.services.dynamodbv2.model.KeyType.RANGE;
import static com.amazonaws.services.dynamodbv2.model.ProjectionType.ALL;
import static com.amazonaws.services.dynamodbv2.model.ProjectionType.KEYS_ONLY;
import static com.amazonaws.services.dynamodbv2.model.ScalarAttributeType.N;
import static com.amazonaws.services.dynamodbv2.model.ScalarAttributeType.S;

public class UserStoreExtension extends DynamoExtension implements AfterEachCallback {

    public static final String USER_CREDENTIALS_TABLE = "local-user-credentials";
    public static final String USER_PROFILE_TABLE = "local-user-profile";
    public static final String EMAIL_FIELD = "Email";
    public static final String SUBJECT_ID_FIELD = "SubjectID";
    public static final String PUBLIC_SUBJECT_ID_FIELD = "PublicSubjectID";

    public static final String ACCOUNT_VERIFIED_FIELD = "accountVerified";
    public static final String SUBJECT_ID_INDEX = "SubjectIDIndex";
    public static final String PUBLIC_SUBJECT_ID_INDEX = "PublicSubjectIDIndex";

    public static final String VERIFIED_ACCOUNT_ID_INDEX = "VerifiedAccountIndex";


    private DynamoService dynamoService;

    public boolean userExists(String email) {
        return dynamoService.userExists(email);
    }

    public String getEmailForUser(Subject subject) {
        var credentials = dynamoService.getUserCredentialsFromSubject(subject.getValue());
        return credentials.getEmail();
    }

    public String getPasswordForUser(String email) {
        var credentials = dynamoService.getUserCredentialsFromEmail(email);
        return credentials.getPassword();
    }

    public String getPublicSubjectIdForEmail(String email) {
        return dynamoService
                .getUserProfileByEmailMaybe(email)
                .map(u -> u.getPublicSubjectID())
                .orElseThrow();
    }

    public Optional<String> getPhoneNumberForUser(String email) {
        return dynamoService.getPhoneNumber(email);
    }

    public void signUp(String email, String password) {
        signUp(email, password, new Subject());
    }

    public String signUp(String email, String password, Subject subject) {
        TermsAndConditions termsAndConditions =
                new TermsAndConditions("1.0", LocalDateTime.now(ZoneId.of("UTC")).toString());
        dynamoService.signUp(email, password, subject, termsAndConditions);
        return dynamoService.getUserProfileByEmail(email).getPublicSubjectID();
    }

    public void updateConsent(String email, ClientConsent clientConsent) {
        dynamoService.updateConsent(email, clientConsent);
    }

    public void addPhoneNumber(String email, String phoneNumber) {
        dynamoService.updatePhoneNumber(email, phoneNumber);
        dynamoService.updatePhoneNumberAndAccountVerifiedStatus(email, true);
    }

    public void setPhoneNumberVerified(String email, boolean isVerified) {
        dynamoService.updatePhoneNumberAndAccountVerifiedStatus(email, isVerified);
    }

    public List<MFAMethod> getMfaMethod(String email) {
        return dynamoService.getUserCredentialsFromEmail(email).getMfaMethods();
    }

    public byte[] addSalt(String email) {
        UserProfile userProfile = dynamoService.getUserProfileByEmailMaybe(email).orElseThrow();

        return dynamoService.getOrGenerateSalt(userProfile);
    }

    public Optional<List<ClientConsent>> getUserConsents(String email) {
        return dynamoService.getUserConsents(email);
    }

    public void updateTermsAndConditions(String email, String version) {
        dynamoService.updateTermsAndConditions(email, version);
    }

    public void addMfaMethod(
            String email,
            MFAMethodType mfaMethodType,
            boolean isVerified,
            boolean isEnabled,
            String credentialValue) {
        dynamoService.updateMFAMethod(email, mfaMethodType, isVerified, isEnabled, credentialValue);
    }

    public void updateMFAMethod(
            String email,
            MFAMethodType mfaMethodType,
            boolean methodVerified,
            boolean enabled,
            String credentialValue) {
        dynamoService.updateMFAMethod(
                email, mfaMethodType, methodVerified, enabled, credentialValue);
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        super.beforeAll(context);
        dynamoService =
                new DynamoService(
                        new DynamoTestConfiguration(REGION, ENVIRONMENT, DYNAMO_ENDPOINT));
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        clearDynamoTable(dynamoDB, USER_CREDENTIALS_TABLE, EMAIL_FIELD);
        clearDynamoTable(dynamoDB, USER_PROFILE_TABLE, EMAIL_FIELD);
    }

    @Override
    protected void createTables() {
        if (!tableExists(USER_PROFILE_TABLE)) {
            createUserProfileTable(USER_PROFILE_TABLE);
        }

        if (!tableExists(USER_CREDENTIALS_TABLE)) {
            createUserCredentialsTable(USER_CREDENTIALS_TABLE);
        }
    }

    private void createUserCredentialsTable(String tableName) {
        CreateTableRequest request =
                new CreateTableRequest()
                        .withTableName(tableName)
                        .withKeySchema(new KeySchemaElement(EMAIL_FIELD, HASH))
                        .withBillingMode(BillingMode.PAY_PER_REQUEST)
                        .withAttributeDefinitions(
                                new AttributeDefinition(EMAIL_FIELD, S),
                                new AttributeDefinition(SUBJECT_ID_FIELD, S))
                        .withGlobalSecondaryIndexes(
                                new GlobalSecondaryIndex()
                                        .withIndexName(SUBJECT_ID_INDEX)
                                        .withKeySchema(new KeySchemaElement(SUBJECT_ID_FIELD, HASH))
                                        .withProjection(new Projection().withProjectionType(ALL)));
        dynamoDB.createTable(request);
    }

    private void createUserProfileTable(String tableName) {
        CreateTableRequest request =
                new CreateTableRequest()
                        .withTableName(tableName)
                        .withKeySchema(new KeySchemaElement(EMAIL_FIELD, HASH))
                        .withBillingMode(BillingMode.PAY_PER_REQUEST)
                        .withAttributeDefinitions(
                                new AttributeDefinition(EMAIL_FIELD, S),
                                new AttributeDefinition(SUBJECT_ID_FIELD, S),
                                new AttributeDefinition(PUBLIC_SUBJECT_ID_FIELD, S),
                                new AttributeDefinition(ACCOUNT_VERIFIED_FIELD, N))
                        .withGlobalSecondaryIndexes(
                                new GlobalSecondaryIndex()
                                        .withIndexName(SUBJECT_ID_INDEX)
                                        .withKeySchema(new KeySchemaElement(SUBJECT_ID_FIELD, HASH))
                                        .withProjection(new Projection().withProjectionType(ALL)),
                                new GlobalSecondaryIndex()
                                        .withIndexName(PUBLIC_SUBJECT_ID_INDEX)
                                        .withKeySchema(
                                                new KeySchemaElement(PUBLIC_SUBJECT_ID_FIELD, HASH))
                                        .withProjection(new Projection().withProjectionType(ALL)),
                                new GlobalSecondaryIndex()
                                        .withIndexName(VERIFIED_ACCOUNT_ID_INDEX)
//                                        .withKeySchema(new KeySchemaElement().withAttributeName(SUBJECT_ID_FIELD).withKeyType(HASH), // Partition
//                                                // key
//                                                new KeySchemaElement().withAttributeName(ACCOUNT_VERIFIED_FIELD).withKeyType(RANGE))
                                        .withKeySchema(
                                                new KeySchemaElement(SUBJECT_ID_FIELD, HASH),
                                                new KeySchemaElement(ACCOUNT_VERIFIED_FIELD, RANGE))
                                        .withProjection(new Projection().withProjectionType(KEYS_ONLY))
                                        );
        dynamoDB.createTable(request);
    }
}
