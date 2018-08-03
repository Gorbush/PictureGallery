package gallerymine.test.ui;

import org.junit.runner.RunWith;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;

@RunWith(Cucumber.class)
@CucumberOptions(features = "src/test/resources/setup")
public class CucumberSetupTest extends SpringIntegrationTest {

}