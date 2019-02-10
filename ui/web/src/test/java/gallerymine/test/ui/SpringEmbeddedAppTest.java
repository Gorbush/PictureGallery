package gallerymine.test.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gallerymine.GalleryMineApplication;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

//@SpringBootTest(classes = GalleryMineApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@SpringBootTest(classes = GalleryMineApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration
public class SpringEmbeddedAppTest extends SpringIntegrationTest {

    private static Logger log = LoggerFactory.getLogger(SpringEmbeddedAppTest.class);

    static ResponseEntity<HashMap> latestResponseMap = null;

    @Autowired
    private ObjectMapper jacksonObjectMapper;

    static Map<String, Object> context = new HashMap<>();

    @LocalServerPort
    public int serverPort;

    @Autowired
    protected RestTemplate restTemplate;

    public static String unquote(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length()-1);
        }
        if (value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length()-1);
        }
        return value;
    }

    protected void updateLastResponse(ResponseEntity<HashMap> lrm) {
        latestResponseMap = lrm;
        context.put("response", lrm);
    }

    public void runPostRequestSuccess(Object requestEntity, String url) {
        runPostRequest(requestEntity, url);
        checkResponseOK();
    }

    public void runGetRequestSuccess(String url) throws IOException {
        runGetRequest(url);
        checkResponseOK();
    }

    private void checkResponseOK() {
        final HttpStatus currentStatusCode = latestResponseMap.getStatusCode();
        assertThat("status code is incorrect : " + latestResponseMap.getStatusCode(), currentStatusCode.value(), is(200));
    }

    public void runPostRequest(Object requestEntity, String url) {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Accept", "application/json");
        headers.add("Content-Type", "application/json; charset=UTF-8");
        HttpEntity request = new HttpEntity(requestEntity, headers);
        try {
            updateLastResponse(restTemplate.exchange("http://localhost:" + serverPort + url, HttpMethod.POST, request, HashMap.class));
        } catch (HttpClientErrorException e) {
            String content = "";
            if (requestEntity != null) {
                try {
                    content = jacksonObjectMapper.writeValueAsString(requestEntity);
                } catch (JsonProcessingException e1) {
                    log.error("Failed to serialize POST content for log", e);
                }
            }
            log.error("POST failed to url={} content=\n{}\n", "http://localhost:" + serverPort + url, content, e);
            throw e;
        }
    }

    public void runGetRequest(String url) throws IOException {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Accept", "application/json");
        headers.add("Content-Type", "application/json; charset=UTF-8");
        HttpEntity request = new HttpEntity(null, headers);
        try {
            updateLastResponse(restTemplate.exchange("http://localhost:" + serverPort + url, HttpMethod.GET, request, HashMap.class));
        } catch (HttpClientErrorException e) {
            log.error("GET failed to url={}", "http://localhost:" + serverPort + url, e);
            throw e;
        }
    }

    public <T> T resolveExpression(String expression) {
        return resolveExpression(expression , null);
    }

    public <T> T resolveExpression(String expression, Class<T> clazzRequired) {
        SpelParserConfiguration config = new SpelParserConfiguration(true,true);
        ExpressionParser parser = new SpelExpressionParser(config);
        StandardEvaluationContext contextEL = new StandardEvaluationContext(context);
        contextEL.addPropertyAccessor(new MapAccessor());
        Expression exp = parser.parseExpression(expression);
        if (clazzRequired != null) {
            return exp.getValue(contextEL, clazzRequired);
        } else {
            return (T)exp.getValue(contextEL);
        }
    }

    /** Put value into context */
    public void put(String key, Object value) {
        context.put(key, value);
    }

    /** Put value into context */
    public <T> T get(String key) {
        return (T)context.get(key);
    }

}