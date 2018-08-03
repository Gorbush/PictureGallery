package gallerymine.test.ui;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StepDefsDatabaseLowLevelTest extends SpringIntegrationTest {

    private static Logger log = LoggerFactory.getLogger(StepDefsDatabaseLowLevelTest.class);

    @Autowired
    protected MongoTemplate template = null;

    @Given("^check if test database$")
    public void checkIfTestDatabase() throws Throwable {
        runGetRequestSuccess("/configprops");
        String databaseName = resolveExpression("response.body['spring.data.mongodb-org.springframework.boot.autoconfigure.mongo.MongoProperties'].properties.uri", String.class);
        assertTrue("Database is not test!", databaseName.endsWith("/galleryMineTest"));
    }

    @Given("^run import for test folder$")
    public void runImportForTestFolder() throws Throwable {
        log.info("Initiated import of test folder");
        runGetRequestSuccess("/importing/import?test=true");

        String importRequestId = resolveExpression("response.body.id", String.class);
        put("importRequestId", importRequestId);
        log.info("Import Request id={}", importRequestId);
    }

    @Given("^wait import request become (.*) for (\\d+) seconds$")
    public void waitImportRequestStatus(String statusExpected, long seconds) throws Throwable {
        long startedAt = System.currentTimeMillis();
        String importRequestId = get("importRequestId");
        String statusCurrent;
        do {
            runGetRequestSuccess("/importing/" + importRequestId);
            statusCurrent = resolveExpression("response.body.result.status");
            Thread.sleep(1000);
            log.info("  import request id={} status={} expected status={}", importRequestId, statusCurrent, statusExpected);
        } while (
                    ((startedAt+seconds*100) < System.currentTimeMillis())
                            &&
                    !statusExpected.equals(statusCurrent)
                );
        assertEquals("Status of import request is not right", statusExpected, statusCurrent);
        log.info(" import request id={} status={} in {} ms", importRequestId, statusCurrent, System.currentTimeMillis() - startedAt);
    }

    @When("^database cleanup all$")
    public void databaseCleanupAll() {
        template.dropCollection("customer");
        template.dropCollection("fileInformation");
        template.dropCollection("getCodeRequest");
        template.dropCollection("importRequest");
        template.dropCollection("importSource");
        template.dropCollection("indexRequest");
        template.dropCollection("picture");
        template.dropCollection("pictureFolder");
        template.dropCollection("process");
        template.dropCollection("source");
        template.dropCollection("thumbRequest");
    }

    @When("^database cleanup but gallery")
    public void databaseCleanupAllButGallery() {
        template.dropCollection("customer");
        template.dropCollection("fileInformation");
        template.dropCollection("getCodeRequest");
        template.dropCollection("importRequest");
        template.dropCollection("importSource");
        template.dropCollection("indexRequest");
//        template.dropCollection("picture");
//        template.dropCollection("pictureFolder");
        template.dropCollection("process");
        template.dropCollection("source");
        template.dropCollection("thumbRequest");
    }

}