package sisa.controller;

import sisa.entity.Notification;
import sisa.entity.Role;
import sisa.entity.User;
import sisa.repository.NotificationRepository;
import sisa.repository.UserRepository;
import sisa.service.AnnouncementService;
import sisa.service.MessagingService;
import sisa.service.ParentTeacherContactService;
import sisa.service.TeacherMessageTargetService;
import sisa.service.dto.MessageForm;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CommsInboxController {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final MessagingService messagingService;
    private final AnnouncementService announcementService;
    private final ParentTeacherContactService parentTeacherContactService;
    private final TeacherMessageTargetService teacherMessageTargetService;

    public CommsInboxController(UserRepository userRepository, NotificationRepository notificationRepository,
                                MessagingService messagingService, AnnouncementService announcementService,
                                ParentTeacherContactService parentTeacherContactService,
                                TeacherMessageTargetService teacherMessageTargetService) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.messagingService = messagingService;
        this.announcementService = announcementService;
        this.parentTeacherContactService = parentTeacherContactService;
        this.teacherMessageTargetService = teacherMessageTargetService;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    private void addTeacherOptions(User user, Model model) {
        if (user.getRole() == Role.PARENT) {
            model.addAttribute("teacherOptions", parentTeacherContactService.teachersForParent(user.getUserId()));
        } else if (user.getRole() == Role.STUDENT) {
            model.addAttribute("teacherOptions", parentTeacherContactService.teachersForStudent(user.getUserId()));
        } else if (user.getRole() == Role.TEACHER) {
            model.addAttribute("teacherClassOptions", teacherMessageTargetService.classesTaughtBy(user.getUserId()));
        }
    }

    @GetMapping("/inbox")
    public String inbox(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "comm");
        model.addAttribute("notifications",
                notificationRepository.findByRecipientUserIdAndSentAtIsNotNullOrderBySentAtDesc(user.getUserId()));
        model.addAttribute("messageRows", messagingService.threadsFor(user.getUserId()).stream()
                .filter(t -> t.unreadCount() > 0 || t.lastMessage().getToUserId().equals(user.getUserId()))
                .toList());
        return "comms/inbox";
    }

    @GetMapping("/inbox/{id}")
    public String view(@PathVariable Long id, Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "comm");

        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No such notification: " + id));
        if (!notification.getRecipientUserId().equals(user.getUserId())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "That notification isn't yours.");
        }
        if (notification.getSentAt() == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "That notification hasn't been sent yet.");
        }
        if (!notification.isRead()) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
        model.addAttribute("notification", notification);
        return "comms/inbox-detail";
    }

    @PostMapping("/inbox/{id}/delete")
    public String deleteFromInbox(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        announcementService.deleteFromInbox(id, currentUser(authentication));
        redirectAttributes.addFlashAttribute("success", "Notification deleted from your inbox.");
        return "redirect:/inbox";
    }

    @GetMapping("/messages")
    public String threads(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "comm");
        model.addAttribute("threads", messagingService.threadsFor(user.getUserId()));
        return "comms/messages";
    }

    @GetMapping("/messages/new")
    public String newThreadForm(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "comm");
        addTeacherOptions(user, model);
        model.addAttribute("form", new MessageForm());
        return "comms/message-new";
    }

    @PostMapping("/messages/new")
    public String startThread(@ModelAttribute("form") MessageForm form,
                              @RequestParam(required = false) String sendTo,
                              @RequestParam(required = false) String className,
                              @RequestParam(required = false) String studentId,
                              Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        try {
            if (user.getRole() == Role.TEACHER && sendTo != null) {
                java.util.List<String> recipients = teacherMessageTargetService.resolve(user, sendTo, className, studentId);
                for (String recipient : recipients) messagingService.send(user, recipient, form.getBody());
                if (recipients.size() == 1) return "redirect:/messages/" + recipients.get(0);
                redirectAttributes.addFlashAttribute("success", "Message sent to " + recipients.size() + " people.");
                return "redirect:/messages";
            }
            messagingService.send(user, form.getToUserId(), form.getBody());
            return "redirect:/messages/" + form.getToUserId();
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (RuntimeException ex) {
            model.addAttribute("user", user);
            model.addAttribute("activeItem", "comm");
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("form", form);
            model.addAttribute("sendTo", sendTo);
            model.addAttribute("className", className);
            model.addAttribute("studentId", studentId);
            addTeacherOptions(user, model);
            return "comms/message-new";
        }
    }

    @GetMapping("/messages/{partnerId}")
    public String thread(@PathVariable String partnerId, Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "comm");

        messagingService.markThreadRead(user.getUserId(), partnerId);
        model.addAttribute("partner", userRepository.findById(partnerId).orElse(null));
        model.addAttribute("partnerId", partnerId);
        model.addAttribute("messages", messagingService.threadWith(user.getUserId(), partnerId));
        model.addAttribute("form", new MessageForm());
        return "comms/thread";
    }

    @PostMapping("/messages/{partnerId}")
    public String reply(@PathVariable String partnerId, @ModelAttribute("form") MessageForm form,
                        Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        try {
            messagingService.send(user, partnerId, form.getBody());
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/messages/" + partnerId;
    }
}
