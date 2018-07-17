package gallerymine.frontend.mvc;

import com.twelvemonkeys.servlet.image.IIOProviderContextListener;
import org.springframework.boot.web.servlet.ServletContextInitializer;

import javax.annotation.ManagedBean;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

@ManagedBean
public class ExecutorListener implements ServletContextInitializer {

    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        servletContext.addListener(IIOProviderContextListener.class);
    }
}
