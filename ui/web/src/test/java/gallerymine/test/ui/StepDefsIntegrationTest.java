package gallerymine.test.ui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cucumber.api.java.en.Given;
import gallerymine.model.mvc.SourceCriteria;
import gallerymine.model.support.PictureGrade;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import cucumber.api.java.en.And;
import cucumber.api.java.en.Then;

public class StepDefsIntegrationTest extends SpringIntegrationTest {

    private static Logger log = LoggerFactory.getLogger(StepDefsIntegrationTest.class);

    @Autowired
    private ObjectMapper jacksonObjectMapper;

    @Given("^api call /listFolders (.+)")
    public void the_client_issues_list_folders(String path) {
        SourceCriteria searchCriteria = new SourceCriteria();
        searchCriteria.setPage(0);
        searchCriteria.setSize(10);
        searchCriteria.setPath(unquote(path));
        searchCriteria.setGrade(PictureGrade.GALLERY);
        runPostRequest(searchCriteria, "/sources/listFolders");
    }

    @Given("^api call /findPath (.+)")
    public void the_client_issues_findPath(String path) {
        SourceCriteria searchCriteria = new SourceCriteria();
        searchCriteria.setPage(0);
        searchCriteria.setSize(10);
        searchCriteria.setPath(unquote(path));
        searchCriteria.setGrade(PictureGrade.GALLERY);
        runPostRequest(searchCriteria, "/sources/findPath");
    }

    @Then("^response status code is (\\d+)$")
    public void checkResponseStatusCode(int statusCode) {
        final HttpStatus currentStatusCode = latestResponseMap.getStatusCode();
        assertThat("status code is incorrect : " + latestResponseMap.getStatusCode(), currentStatusCode.value(), is(statusCode));
    }

    @And("^response has (.+)$")
    public void responseHas(String key) {
        assertTrue("Key not found in response key='"+key+"'", latestResponseMap.getBody().containsKey(key));
    }

    @And("^response key (.+) equal to (.+)$")
    public void responseValue(String key, String valueStr) {
        Object valueExpected = resolveExpression(valueStr);
        Object valueActual = valueExpected == null ? resolveExpression(key) : resolveExpression(key, valueExpected.getClass());
        assertEquals("Value not equal for '"+key+"' which is '"+valueActual+"' != '"+valueExpected+"'",valueExpected, valueActual);
    }

    @And("^print (.+)$")
    public void printValue(String expr) {
        Object value = resolveExpression(expr);
        StringBuilder out = new StringBuilder();
        out.append(expr).append(" = ");
        try {
            out.append(jacksonObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
        } catch (JsonProcessingException e) {
            out.append(" <Failed to render>");
            log.error("Failed to render value {}", expr, e);
        }
        log.info(out.toString());
    }

}