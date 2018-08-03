package gallerymine.test.ui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import cucumber.api.java.en.Given;
import gallerymine.model.mvc.SourceCriteria;
import gallerymine.model.support.PictureGrade;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import cucumber.api.java.en.And;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;

public class StepDefsIntegrationTest extends SpringIntegrationTest {

    @When("^the client calls /baeldung$")
    public void the_client_issues_POST_hello() throws Throwable {
        executePost();
    }

    @Given("^the client calls /hello$")
    public void the_client_issues_GET_hello() throws Throwable {
        executeGet("http://localhost:"+serverPort+"/hello");
    }

    @Given("^api call /listFolders")
    public void the_client_issues_list_folders() {
        SourceCriteria searchCriteria = new SourceCriteria();
        searchCriteria.setPage(0);
        searchCriteria.setSize(10);
        searchCriteria.setPath("test");
        searchCriteria.setGrade(PictureGrade.GALLERY);
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Accept", "application/json");
        headers.add("Content-Type", "application/json; charset=UTF-8");
        HttpEntity<SourceCriteria> request = new HttpEntity<>(searchCriteria, headers);
        latestResponseMap = restTemplate .exchange("http://localhost:"+serverPort+"/sources/findPath", HttpMethod.POST, request, HashMap.class);
        System.out.print("Run");
    }

    @When("^the client calls /version$")
    public void the_client_issues_GET_version() throws Throwable {
        executeGet("http://localhost:"+serverPort+"/version");
    }

    @Then("^response status code is (\\d+)$")
    public void the_client_receives_status_code_of(int statusCode) {
        final HttpStatus currentStatusCode = latestResponseMap.getStatusCode();
        assertThat("status code is incorrect : " + latestResponseMap.getStatusCode(), currentStatusCode.value(), is(statusCode));
    }

    @And("^the client receives server version (.+)$")
    public void the_client_receives_server_version_body(String version)  {
        assertThat(latestResponse.getBody(), is(version));
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

    public Object resolveExpression(String expression) {
        return resolveExpression(expression , null);
    }

    public Object resolveExpression(String expression, Class clazzRequired) {
        SpelParserConfiguration config = new SpelParserConfiguration(true,true);
        ExpressionParser parser = new SpelExpressionParser(config);
        StandardEvaluationContext context = new StandardEvaluationContext(latestResponseMap);
        context.addPropertyAccessor(new MapAccessor());
        Expression exp = parser.parseExpression(expression);
        if (clazzRequired != null) {
            return exp.getValue(context, clazzRequired);
        } else {
            return exp.getValue(context);
        }
    }
}