package gallerymine;

import gallerymine.backend.beans.AppConfig;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.boot.autoconfigure.web.ResourceProperties;
import org.springframework.boot.autoconfigure.web.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.web.WebMvcProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * Resource resolvers for thumbs, sources and gallery
 * http://www.baeldung.com/spring-mvc-static-resources
 * Created by sergii_puliaiev on 6/20/17.
 */
@Configuration
@EnableWebMvc
public class WebAppConfig extends WebMvcConfigurerAdapter {

    @Autowired
    private AppConfig appConfig;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        super.addResourceHandlers(registry);
        // AddThumbs
        registry
                .addResourceHandler("/thumbs/**")
                .addResourceLocations("file://"+appConfig.getThumbsRootFolder())
                .setCachePeriod(3600);
        // Add Gallery Sources
        registry
                .addResourceHandler("/srcs/**")
                .addResourceLocations("file://"+appConfig.getSourcesRootFolder())
                .setCachePeriod(3600);
        // Add Gallery Approved Pictures
        registry
                .addResourceHandler("/pics/**")
                .addResourceLocations("file://"+appConfig.getGalleryRootFolder())
                .setCachePeriod(3600);
    }
}

