package uk.gov.di.authentication.utils.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.gov.di.authentication.shared.entity.BulkEmailStatus;
import uk.gov.di.authentication.shared.services.BulkEmailUsersService;
import uk.gov.di.authentication.shared.services.ConfigurationService;
import uk.gov.di.authentication.shared.services.DynamoService;

public class BulkUserEmailAudienceLoaderScheduledEventHandler
        implements RequestHandler<ScheduledEvent, Void> {

    private static final Logger LOG =
            LogManager.getLogger(BulkUserEmailAudienceLoaderScheduledEventHandler.class);

    private final BulkEmailUsersService bulkEmailUsersService;

    private final DynamoService dynamoService;

    private final ConfigurationService configurationService;

    private long itemCounter = 0;

    public BulkUserEmailAudienceLoaderScheduledEventHandler() {
        this(ConfigurationService.getInstance());
    }

    public BulkUserEmailAudienceLoaderScheduledEventHandler(
            BulkEmailUsersService bulkEmailUsersService,
            DynamoService dynamoService,
            ConfigurationService configurationService) {
        this.bulkEmailUsersService = bulkEmailUsersService;
        this.dynamoService = dynamoService;
        this.configurationService = configurationService;
    }

    public BulkUserEmailAudienceLoaderScheduledEventHandler(
            ConfigurationService configurationService) {
        this.configurationService = configurationService;
        this.bulkEmailUsersService = new BulkEmailUsersService(configurationService);
        this.dynamoService = new DynamoService(configurationService);
    }

    @Override
    public Void handleRequest(ScheduledEvent event, Context context) {
        LOG.info("Bulk User Email audience load triggered.");

        itemCounter = 0;
        dynamoService
                .getBulkUserEmailAudienceStream()
                .forEach(
                        userProfile -> {
                            itemCounter++;
                            bulkEmailUsersService.addUser(
                                    userProfile.getSubjectID(), BulkEmailStatus.PENDING);
                            LOG.info("Bulk User Email added item number: {}", itemCounter);
                        });

        LOG.info("Bulk User Email audience load complete.  Total users added: {}", itemCounter);
        return null;
    }
}
