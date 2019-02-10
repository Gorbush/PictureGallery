package gallerymine.test.ui;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import gallerymine.GalleryMineApplication;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

@RunWith(Cucumber.class)
@CucumberOptions(features = "src/test/resources/functional")
@SpringBootTest(classes = GalleryMineApplicationTest.class, webEnvironment=NONE)
@ContextConfiguration
public class CucumberOverRunningTest extends SpringIntegrationTest {


}