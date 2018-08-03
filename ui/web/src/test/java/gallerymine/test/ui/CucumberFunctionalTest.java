package gallerymine.test.ui;

import org.junit.runner.RunWith;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;

@RunWith(Cucumber.class)
@CucumberOptions(features = "src/test/resources/functional")
public class CucumberFunctionalTest extends SpringIntegrationTest {

}