package jug.org.qr;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Controller
public class BadgeController {

    @Autowired
    private BadgeService badgeService;

    private final ConcurrentHashMap<String, byte[]> pdfCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public BadgeController() {
        // Clean up cache entries after 5 minutes
        scheduler.scheduleAtFixedRate(() -> {
            pdfCache.clear();
        }, 5, 5, TimeUnit.MINUTES);
    }

    @GetMapping("/")
    public String index(Model model, @RequestParam(required = false) String loading) {
        if (loading != null) {
            model.addAttribute("showLoading", true);
        }
        return "index";
    }

    @PostMapping("/generate")
    public String generateBadges(@RequestParam("file") MultipartFile file, 
                               RedirectAttributes redirectAttributes) {
        try {
            byte[] pdfBytes = badgeService.generateBadges(file);
            String fileId = UUID.randomUUID().toString();
            pdfCache.put(fileId, pdfBytes);
            
            redirectAttributes.addFlashAttribute("success", "Badges generated successfully!");
            return "redirect:/download?fileId=" + fileId;
        } catch (Exception e) {
            if (e.getMessage().contains("No valid attendees found")) {
                redirectAttributes.addFlashAttribute("error", "No valid rows found in CSV. Please check your file format.");
            } else {
                redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
            }
            return "redirect:/";
        }
    }

    @PostMapping("/quick-generate")
    public String quickGenerateBadge(
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam("company") String company,
            RedirectAttributes redirectAttributes) {
        try {
            Attendee attendee = new Attendee(name, "", email, company);
            byte[] pdfBytes = badgeService.generateSingleBadge(attendee);
            String fileId = UUID.randomUUID().toString();
            pdfCache.put(fileId, pdfBytes);
            
            redirectAttributes.addFlashAttribute("success", "Badge generated successfully!");
            return "redirect:/download?fileId=" + fileId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/download")
    public void download(
            @RequestParam("fileId") String fileId,
            HttpServletResponse response) throws IOException {
        byte[] pdfBytes = pdfCache.remove(fileId);
        if (pdfBytes == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        
        // Set response headers
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=badges.pdf");
        response.setContentLength(pdfBytes.length);
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        
        // Write the PDF bytes to the response
        try (OutputStream out = response.getOutputStream()) {
            out.write(pdfBytes);
            out.flush();
        }
    }
} 