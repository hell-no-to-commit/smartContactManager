package com.smartcontact.controller;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.config.web.server.ServerHttpSecurity.HttpsRedirectSpec;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.mysql.cj.exceptions.ClosedOnExpiredPasswordException;
import com.smartcontact.dao.ContactRepository;
import com.smartcontact.dao.UserRepository;
import com.smartcontact.entities.Contact;
import com.smartcontact.entities.User;
import com.smartcontact.helper.Message;

@Controller
@RequestMapping("/user")
public class UserController {

	@Autowired
	private BCryptPasswordEncoder bCryptPasswordEncoder;
	
	@Autowired
	private UserRepository userRepository;
	
	@Autowired
	private ContactRepository contactRepository;
	
	//method for adding common data
	@ModelAttribute
	public void addCommonData(Model model, Principal principal) {
		String username = principal.getName();
		System.out.println("USERNAME: " + username);

		User user = userRepository.getUserByUserName(username);

		System.out.println("USER DETAILS: " + user);

		model.addAttribute("user", user);
	}

	// dashboard home
	@RequestMapping("/index")
	public String dashboard(Model model, Principal principal) {
		// get the user using username
		return "normal/user_dashboard";
	}

	// open add form handler
	@GetMapping("/add-contact")
	public String openAddContactForm(Model model) {
		model.addAttribute("title", "Add Contact");
		model.addAttribute("contact", new Contact());

		return "normal/add_contact_form";
	}

	// processing add contact form
	@PostMapping("/process-contact")
	public String processContact(@ModelAttribute Contact contact, @RequestParam("profileImage") MultipartFile file,
			Principal principal, HttpSession session ) {

		try {

				String name = principal.getName();
				User user = this.userRepository.getUserByUserName(name);
	
				// processing and uploading the file
				if (file.isEmpty()) {
					// if file is empty, try our msg
					System.out.println("Image file is empty");
					contact.setImage("contact.png");
	
				} else {
					// upload file to folder and update the name to contact
					contact.setImage(file.getOriginalFilename());
	
					File saveFile = new ClassPathResource("static/img").getFile();
	
					Path path = Paths.get(saveFile.getAbsolutePath() + File.separator + file.getOriginalFilename());
	
					Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
	
					System.out.println("Image is uploaded");
	
				}
	
				contact.setUser(user);
	
				user.getContacts().add(contact);
	
				this.userRepository.save(user);
	
				System.out.println("Contact details:" + contact);
				System.out.println("Added to database");
				
				//success message
				session.setAttribute("message", new Message("Your contact is added..", "success"));
				 
				
		} catch (Exception e) {
			System.out.println("ERROR " + e.getMessage());
			e.printStackTrace();
			
			//Error message
			session.setAttribute("message", new Message("Something went wrong! try again..", "danger"));
			
		}

		return "normal/add_contact_form";
	}

	//show contacts handler
	//showing 5 contacts per page
	
	@GetMapping("/show-contacts/{page}")
	public String showContacts(@PathVariable("page") Integer page ,Model m, Principal principal) {
		
		m.addAttribute("title", "Show User Contatcs");
		
//		//sending contact list
		
		String userName = principal.getName();
		
		User user = this.userRepository.getUserByUserName(userName);
		
		Pageable pageable  = PageRequest.of(page, 5);
		
		Page<Contact> contacts = this.contactRepository.findconContactsByUser(user.getId(), pageable);
		
		m.addAttribute("contacts", contacts);
		m.addAttribute("currentPage", page);
		m.addAttribute("totalPages", contacts.getTotalPages());
		
		return "normal/show_contacts";
	}
	
	//Showing particular contact details
	@RequestMapping("/{cId}/contact")
	public String showContactDetail(@PathVariable("cId") Integer cId, Model model, Principal principal) {
		System.out.println("CID "+cId);
		
		Optional<Contact> contactOptional = this.contactRepository.findById(cId);
		Contact contact = contactOptional.get();
		
		String userName = principal.getName();
		User user = this.userRepository.getUserByUserName(userName);
		
		if(user.getId()==contact.getUser().getId()) {
			
			model.addAttribute("contact", contact);
			model.addAttribute("title", contact.getName());
		}
		
		return "normal/contact_detail";
	}
	
	//delete contact handler
	
	@GetMapping("/delete/{cid}")
	public String deleteContact(@PathVariable("cid") Integer cId, Model model, HttpSession session, Principal principal){
		
		Contact contact = this.contactRepository.findById(cId).get();
		
		System.out.println("Contact "+contact.getcId());
		
		//contact.setUser(null);
		
		//this.contactRepository.delete(contact);
		
		User user = this.userRepository.getUserByUserName(principal.getName());
		
		user.getContacts().remove(contact);
		
		this.userRepository.save(user);
		
		
		session.setAttribute("message",new Message("Contact deleted successfully..", "success"));
		
		return "redirect:/user/show-contacts/0";
	}
	
	//open update form handler
	
	@PostMapping("/update-contact/{cid}")
	public String updateForm(@PathVariable("cid") Integer cid, Model m) {
		
		m.addAttribute("title","Update Contact");
		
		Contact contact = this.contactRepository.findById(cid).get();
		
		m.addAttribute("contact",contact);
		
		return "normal/update_form";
	}
	
	//update Contact handler
	
	@RequestMapping(value = "/process-update", method=RequestMethod.POST)
	public String updateHandler(@ModelAttribute Contact contact, 
			@RequestParam("profileImage") MultipartFile file, 
			Model m, 
			HttpSession session,
			Principal principal) {
		
		try {
			
			//old contact details
			Contact oldContactDetail= this.contactRepository.findById(contact.getcId()).get();
			
			if(!file.isEmpty()) {
			
			//delete old photo
			
				File deleteFile = new ClassPathResource("static/img").getFile();
				
				File file1 = new File(deleteFile, oldContactDetail.getImage());
				
				file1.delete();
				

				
			//update new photo
				
				File saveFile = new ClassPathResource("static/img").getFile();
				
				Path path = Paths.get(saveFile.getAbsolutePath() + File.separator + file.getOriginalFilename());

				Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

				contact.setImage(file.getOriginalFilename());
				
			}else {
				contact.setImage(oldContactDetail.getImage());
			}
			
			User user =this.userRepository.getUserByUserName(principal.getName());
			
			contact.setUser(user);
			
			this.contactRepository.save(contact);
			
			session.setAttribute("message",new Message("Contact updated successfully..", "success"));
			
		}catch(Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("CONTACT NAME "+contact.getName());
		System.out.println("CONTACT ID "+contact.getcId());
		
		return"redirect:/user/"+contact.getcId()+"/contact";
	}
	
	//Your Profile Handler
	
	@GetMapping("/profile")
	public String yourProfile(Model model) {
		
		model.addAttribute("title","Profile Page");
		
		return "/normal/profile";
	}
	
	//Open Settings Handler
	@GetMapping("/settings")
	public String openSettings() {
		return "/normal/settings";
	} 
	
	//Change password Handler
	
	@PostMapping("/change-password")
	public String changePassword(@RequestParam("oldPassword") String oldPassword, 
			@RequestParam("newPassword") String newPassword, 
			Principal principal,
			HttpSession session) {
		
		System.out.println("OLD PASSWORD "+oldPassword);
		System.out.println("NEW PASSWORD "+newPassword);
		
		String userName = principal.getName();
		User currentUser = this.userRepository.getUserByUserName(userName);
		
		if(this.bCryptPasswordEncoder.matches(oldPassword, currentUser.getPassword())) {
			
			//change the password
			
			currentUser.setPassword(this.bCryptPasswordEncoder.encode(newPassword));
			this.userRepository.save(currentUser);
			session.setAttribute("message",new Message("Password Changed Successfully..", "success"));
			
		}else {
			//error
			session.setAttribute("message",new Message("Incorrect Old Password..", "danger"));
			return "redirect:/user/settings";
		}
		
		return "redirect:/user/index";
		
	}
}
