package gallerymine.test.ui;

import org.junit.runner.RunWith;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@RunWith(Cucumber.class)
@CucumberOptions(features = "src/test/resources")
public class CucumberIntegrationTest extends SpringIntegrationTest {

}