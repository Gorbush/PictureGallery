package gallerymine;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import gallerymine.backend.beans.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import java.text.SimpleDateFormat;
import java.util.List;
//import uk.co.gcwilliams.jodatime.thymeleaf.JodaTimeDialect;

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

//    @Bean
//    protected SpringTemplateEngine templateEngine(ITemplateResolver templateResolver) {
//        SpringTemplateEngine engine = new SpringTemplateEngine();
//        engine.addDialect(new Java8TimeDialect());
//        engine.addDialect(new JodaTimeDialect());
//        engine.setTemplateResolver(templateResolver);
//        return engine;
//    }

//    @Bean
//    public ThymeleafViewResolver thymeleafViewResolver(ITemplateResolver templateResolver) {
//        ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
//        viewResolver.setTemplateEngine(templateEngine(templateResolver));
//        return viewResolver;
//    }

//    @Override
//    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
//        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
//        builder.serializationInclusion(JsonInclude.Include.NON_NULL);
//        builder.propertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
//        builder.serializationInclusion(JsonInclude.Include.NON_EMPTY);
//        builder.indentOutput(true).dateFormat(new SimpleDateFormat("yyyy-MM-dd"));
//        converters.add(new MappingJackson2HttpMessageConverter(builder.build()));
//        converters.add(new MappingJackson2XmlHttpMessageConverter(builder.createXmlMapper(true).build()));
//    }

}

