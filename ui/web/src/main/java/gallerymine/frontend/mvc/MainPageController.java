package gallerymine.frontend.mvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import gallerymine.backend.beans.repository.FileRepository;
import gallerymine.backend.beans.repository.ProcessRepository;
import gallerymine.backend.services.ProcessService;
import gallerymine.model.Customer;
import gallerymine.model.FileInformation;
import gallerymine.model.Process;
import gallerymine.model.support.ProcessDetails;
import gallerymine.model.support.ProcessStatus;
import gallerymine.model.support.ProcessType;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import javax.websocket.server.PathParam;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/")
public class MainPageController {

    @Autowired
    protected MongoTemplate mongoTemplate = null;

    @Autowired
    FileRepository fileRepository;

    @Autowired
    ProcessRepository processRepository;

    @Autowired
    ProcessService processService;

    @Autowired
    private ObjectMapper jacksonObjectMapper;

    @GetMapping("/version")
    @ResponseBody
    public Map<String, String> getVersion() {
        Map<String, String> info = new HashMap<>();
        info.put("database", mongoTemplate.getDb().getName());
        return info;
    }

    @GetMapping
    public ModelAndView list(Principal principal) {
        Page<FileInformation> files = fileRepository.findAll(new PageRequest(0, 50, new Sort(new Sort.Order(Sort.Direction.DESC, "id"))));
        return new ModelAndView("main/files", "files", files);
    }

    @GetMapping("/files")
    public Object listFiles(HttpServletResponse response, Principal principal) {
        Page<FileInformation> files = fileRepository.findAll(new PageRequest(0, 50, new Sort(new Sort.Order(Sort.Direction.DESC, "id"))));
        if (files.getSize() == 0) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "File Not Found";
        } else {
            return new ModelAndView("main/files", "files", files);
        }
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

    @GetMapping("/importProgress/{importId}")
    public ModelAndView importProgress(Principal principal, @PathVariable("importId") String importProcessId) {

        Process process = processRepository.findOne(importProcessId);
        if (process == null) {
            ModelAndView model = new ModelAndView("main/missing");
            model.addObject("timestamp", new Date());
            model.addObject("path", "");
            model.addObject("error", "Entity not found");
            model.addObject("status", "Failed");
            model.addObject("message", "Import Process not found");
            return model;
        }
        ProcessDetails details = processService.populateAllDetails(process);

        ModelAndView model = new ModelAndView("main/importProgress", "process", process);
        model.addObject("details", details==null ? null : details.getDetails());
        model.addObject("lastDetail", (details==null || details.getLastDetail() == null) ? null : details.getLastDetail());
        try {
            model.addObject("lastDetailJSON", (details == null || details.getLastDetail() == null) ? null :
                    jacksonObjectMapper.writeValueAsString(details.getLastDetail()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return model;
    }

    @GetMapping("/processes?top")
    public ModelAndView listTopActiveProcesses(Principal principal, @RequestAttribute("type") Optional<ProcessType> type) {
        List<Process> processes = processService.getTop(null, type.orElse(null));
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
