package uk.gov.di.authentication.sharedtest.extensions;

import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static com.amazonaws.services.dynamodbv2.model.KeyType.HASH;
import static com.amazonaws.services.dynamodbv2.model.ScalarAttributeType.S;

public class DocumentAppCredentialStoreExtension extends DynamoExtension
        implements AfterEachCallback {

    public static final String CREDENTIAL_REGISTRY_TABLE = "local-doc-app-credential";
    public static final String SUBJECT_ID_FIELD = "SubjectID";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        super.beforeAll(context);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        clearDynamoTable(dynamoDB, CREDENTIAL_REGISTRY_TABLE, SUBJECT_ID_FIELD);
    }

    @Override
    protected void createTables() {
        if (!tableExists(CREDENTIAL_REGISTRY_TABLE)) {
            createClientRegistryTable(CREDENTIAL_REGISTRY_TABLE);
        }
    }

    private void createClientRegistryTable(String tableName) {
        CreateTableRequest request =
                new CreateTableRequest()
                        .withTableName(tableName)
                        .withKeySchema(new KeySchemaElement(SUBJECT_ID_FIELD, HASH))
                        .withBillingMode(BillingMode.PAY_PER_REQUEST)
                        .withAttributeDefinitions(new AttributeDefinition(SUBJECT_ID_FIELD, S));
        dynamoDB.createTable(request);
    }
}