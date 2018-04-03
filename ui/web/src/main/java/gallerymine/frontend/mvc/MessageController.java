/*
 * Copyright 2012-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gallerymine.frontend.mvc;

import javax.validation.Valid;

import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.CustomerRepository;
import gallerymine.backend.beans.repository.PictureRepository;
import gallerymine.backend.helpers.GeoCodeHelper;
import gallerymine.backend.helpers.IndexRequestPoolManager;
import gallerymine.backend.helpers.IndexRequestProcessor;
import gallerymine.model.importer.IndexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import gallerymine.model.Customer;

import java.nio.file.*;

import static gallerymine.frontend.mvc.support.ResponseBuilder.responseError;
import static gallerymine.frontend.mvc.support.ResponseBuilder.responseOk;

/**
 * @author Rob Winch
 * @author Doo-Hwan Kwak
 */
@Controller
@RequestMapping("/mess")
public class MessageController {

	private static Logger log = LoggerFactory.getLogger(MessageController.class);

	@Autowired
    public AppConfig appConfig;

    @Autowired
	private CustomerRepository messageRepository;

	@Autowired
	private PictureRepository pictureRepository;

	private GeoCodeHelper geoCodeHelper;

	@Autowired
	private IndexRequestProcessor indexRequestProcessor;

	@Autowired
	private IndexRequestPoolManager indexRequestPool;

	public MessageController() {
	}

	@GetMapping
	public ModelAndView list() {
		Iterable<Customer> messages = this.messageRepository.findAll();
		return new ModelAndView("messages/list", "messages", messages);
	}

	@GetMapping("{id}")
	public ModelAndView view(@PathVariable("id") Customer message) {
		return new ModelAndView("messages/view", "message", message);
	}

	@GetMapping(params = "form")
	public String createForm(@ModelAttribute Customer message) {
		return "messages/form";
	}

	@PostMapping
	public ModelAndView create(@Valid Customer message, BindingResult result,
			RedirectAttributes redirect) {
		if (result.hasErrors()) {
			return new ModelAndView("messages/form", "formErrors", result.getAllErrors());
		}
		message = this.messageRepository.save(message);
		redirect.addFlashAttribute("globalMessage", "Successfully created a new message");
		return new ModelAndView("redirect:/{message.id}", "message.id", message.getId());
	}

	@RequestMapping("foo")
	public String foo() {
		throw new RuntimeException("Expected exception in controller");
	}

	@GetMapping(value = "delete/{id}")
	public ModelAndView delete(@PathVariable("id") String id) {
		this.messageRepository.delete(id);
		Iterable<Customer> messages = this.messageRepository.findAll();
		return new ModelAndView("messages/list", "messages", messages);
	}

	@GetMapping(value = "modify/{id}")
	public ModelAndView modifyForm(@PathVariable("id") Customer message) {
		return new ModelAndView("messages/form", "message",  message);
	}

	@GetMapping(value = "index")
	public Object index() {
		Path path = Paths.get(appConfig.getGalleryRootFolder());

		try {
			IndexRequest request = indexRequestProcessor.registerNewFolderRequest(path.toAbsolutePath().toString(), null);
			indexRequestPool.executeRequest(request);

			return new ModelAndView("messages/form", "message", responseOk());
		} catch (Exception e) {
			return responseError("Failed to index. Reason: "+e.getMessage(), e);
		}

	}

}
