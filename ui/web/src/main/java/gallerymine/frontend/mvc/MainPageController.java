package gallerymine.frontend.mvc;

import gallerymine.backend.beans.repository.FileRepository;
import gallerymine.model.Customer;
import gallerymine.model.FileInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;

@Controller
@RequestMapping("/")
public class MainPageController {

    @Autowired
    FileRepository fileRepository;

    @GetMapping
    public ModelAndView list(Principal principal) {
        Page<FileInformation> files = fileRepository.findAll(new PageRequest(0, 50, new Sort(new Sort.Order(Sort.Direction.DESC, "id"))));
        return new ModelAndView("main/index", "files", files);
    }

}
