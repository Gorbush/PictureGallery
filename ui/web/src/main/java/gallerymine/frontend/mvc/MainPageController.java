package gallerymine.frontend.mvc;

import gallerymine.backend.beans.repository.FileRepository;
import gallerymine.backend.beans.repository.ProcessRepository;
import gallerymine.backend.services.ProcessService;
import gallerymine.model.Customer;
import gallerymine.model.FileInformation;
import gallerymine.model.Process;
import gallerymine.model.support.ProcessDetails;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Controller
@RequestMapping("/")
public class MainPageController {

    @Autowired
    FileRepository fileRepository;

    @Autowired
    ProcessRepository processRepository;

    @Autowired
    ProcessService processService;

    @GetMapping
    public ModelAndView list(Principal principal) {
        Page<FileInformation> files = fileRepository.findAll(new PageRequest(0, 50, new Sort(new Sort.Order(Sort.Direction.DESC, "id"))));
        return new ModelAndView("main/files", "files", files);
    }

    @GetMapping("/files")
    public ModelAndView listFiles(Principal principal) {
        Page<FileInformation> files = fileRepository.findAll(new PageRequest(0, 50, new Sort(new Sort.Order(Sort.Direction.DESC, "id"))));
        return new ModelAndView("main/files", "files", files);
    }

    @GetMapping("/main")
    public ModelAndView mainPage(Principal principal) {
        Page<FileInformation> files = fileRepository.findAll(new PageRequest(0, 50, new Sort(new Sort.Order(Sort.Direction.DESC, "id"))));
        return new ModelAndView("main/main", "main", files);
    }

    @GetMapping("/processes")
    public ModelAndView listProcesses(Principal principal) {

        Page<Process> processes = processRepository.findAll(
                new PageRequest(0, 50,
                        new Sort(new Sort.Order(Sort.Direction.DESC, "started"))));

        Page<ProcessDetails> detailed = processes.map(processService::populateDetails);

        ModelAndView model = new ModelAndView("main/processes", "processes", detailed);
        return model;
    }

    @GetMapping("/processes?top")
    public ModelAndView listTopActiveProcesses(Principal principal) {

        List<Process> processes = processService.getTop();
        List<ProcessDetails> running = processService.populateDetails(processes);

        ModelAndView model = new ModelAndView("main/processes", "processes", running);
        return model;
    }

    @GetMapping("/test")
    public ModelAndView testMethod(Principal principal) {
        Page<FileInformation> files = fileRepository.findAll(new PageRequest(0, 50, new Sort(new Sort.Order(Sort.Direction.DESC, "id"))));
        ModelAndView model = new ModelAndView("main/test", "files", files);
        model.addObject("standardDate", new Date());
        model.addObject("localDateTime", LocalDateTime.now());
        model.addObject("localDate", LocalDate.now());
        model.addObject("timestamp", Instant.now());
        model.addObject("dt", DateTime.now());
        return model;
    }

}
