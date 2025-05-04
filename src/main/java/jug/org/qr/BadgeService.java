package jug.org.qr;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import jakarta.annotation.PostConstruct;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BadgeService {

    private static final float BADGE_WIDTH = 80f * 2.8346457f; // 80mm in points
    private static final float BADGE_HEIGHT = 80f * 2.8346457f; // 80mm in points
    private static final float MARGIN = 5f;
    private static final float QR_SIZE = 150f;
    private static final float FONT_SIZE = 22f;
    private static final float COMPANY_FONT_SIZE = 14f;

    private File tempFontFile;
    private Font nameFont;
    private Font companyFont;

    public BadgeService() {
        try {
            // Load fonts once and reuse them
            BaseFont baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
            nameFont = new Font(baseFont, FONT_SIZE, Font.BOLD);
            companyFont = new Font(baseFont, COMPANY_FONT_SIZE);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize fonts", e);
        }
    }

    @PostConstruct
    public void initFonts() throws Exception {
        InputStream fontStream = getClass().getClassLoader().getResourceAsStream("dejavu-fonts-ttf-2.37/ttf/DejaVuSans.ttf");
        tempFontFile = File.createTempFile("DejaVuSans", ".ttf");
        tempFontFile.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(tempFontFile)) {
            fontStream.transferTo(out);
        }
        BaseFont baseFont = BaseFont.createFont(tempFontFile.getAbsolutePath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        nameFont = new Font(baseFont, 22, Font.BOLD);
        companyFont = new Font(baseFont, 14);
    }

    public byte[] generateBadges(MultipartFile file) throws Exception {
        List<Attendee> attendees = readAttendeesFromCSV(file);
        if (attendees.isEmpty()) {
            throw new IllegalArgumentException("No valid attendees found in CSV");
        }

        // 80mm x 80mm page size setup
        float mmToPoints = 72f / 25.4f;
        float pageWidth = 80f * mmToPoints;
        float pageHeight = 80f * mmToPoints;
        Document document = new Document(new Rectangle(pageWidth, pageHeight));

        // Get current date and time for filename
        String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        document.setMargins(5f, 5f, 5f, 5f);

        // Create a ByteArrayOutputStream to hold the PDF
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, outputStream);
        document.open();

        for (Attendee attendee : attendees) {
            addBadgeToDocument(document, attendee);
            document.newPage();
        }

        document.close();
        byte[] pdfBytes = outputStream.toByteArray();
        
        // Clean up
        document = null;
        outputStream.close();
        outputStream = null;
        attendees.clear();
        attendees = null;
        
        return pdfBytes;
    }

    private List<Attendee> readAttendeesFromCSV(MultipartFile csvFile) throws Exception {
        List<Attendee> attendees = new ArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new InputStreamReader(csvFile.getInputStream()))) {
            String line;
            boolean firstLine = true;
            int lineNumber = 0;
            int validRows = 0;
            int skippedRows = 0;

            while ((line = br.readLine()) != null) {
                lineNumber++;
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                if (line.trim().isEmpty()) {
                    skippedRows++;
                    continue;
                }

                // Try both delimiters
                String[] columns = line.contains(";") ? line.split(";", -1) : line.split(",", -1);

                if (columns.length >= 3) {
                    String fullName = columns[0] != null ? columns[0].trim() : "";
                    String email = columns[1] != null ? columns[1].trim() : "";
                    String company = columns[2] != null ? columns[2].trim() : "";

                    // Skip rows with missing required fields
                    if (fullName.isEmpty() || email.isEmpty()) {
                        skippedRows++;
                        continue;
                    }

                    String[] nameParts = fullName.split(" ", 2);
                    String name = nameParts.length > 0 ? nameParts[0] : "";
                    String surname = nameParts.length > 1 ? nameParts[1] : "";
                    
                    attendees.add(new Attendee(name, surname, email, company));
                    validRows++;
                } else {
                    skippedRows++;
                }
            }

            System.out.println("Processed CSV: " + validRows + " valid rows, " + skippedRows + " rows skipped");
        }

        return attendees;
    }

    private void addBadgeToDocument(Document document, Attendee attendee) throws Exception {
        int nameFontSize = (attendee.getNameSurname().length() > 12) ? 16 : 22;
        nameFont.setSize(nameFontSize);
        
        Paragraph nameParagraph = new Paragraph(attendee.getNameSurname(), nameFont);
        nameParagraph.setAlignment(Element.ALIGN_CENTER);
        nameParagraph.setSpacingAfter(1f);
        document.add(nameParagraph);

        Paragraph companyParagraph = new Paragraph(attendee.getCompany(), companyFont);
        companyParagraph.setAlignment(Element.ALIGN_CENTER);
        companyParagraph.setSpacingAfter(2f);
        document.add(companyParagraph);

        String vCardData = generateVCard(attendee);
        Image qrImage = generateQRCodeImage(vCardData);

        int qrPixelSize = 150;
        qrImage.scaleToFit(qrPixelSize, qrPixelSize);
        qrImage.setAlignment(Element.ALIGN_CENTER);
        document.add(qrImage);

        document.add(new Paragraph(""));

        // Clean up
        vCardData = null;
        qrImage = null;
    }

    private String generateVCard(Attendee attendee) {
        return "BEGIN:VCARD\n" +
                "VERSION:3.0\n" +
                "N:" + attendee.getSurname() + ";" + attendee.getName() + ";;;\n" +
                "FN:" + attendee.getNameSurname() + "\n" +
                "ORG:" + attendee.getCompany() + "\n" +
                "EMAIL:" + attendee.getEmail() + "\n" +
                "END:VCARD";
    }

    private Image generateQRCodeImage(String data) throws WriterException, IOException, BadElementException {
        int qrPixelSize = 300;
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        BitMatrix bitMatrix = new MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, qrPixelSize, qrPixelSize, hints);
        BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", baos);
        byte[] imageBytes = baos.toByteArray();

        return Image.getInstance(imageBytes);
    }

    public byte[] generateSingleBadge(Attendee attendee) throws Exception {
        // Validate attendee data
        if (attendee == null) {
            throw new IllegalArgumentException("Attendee data is required");
        }
        if (attendee.getName() == null || attendee.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (attendee.getEmail() == null || attendee.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }

        // 80mm x 80mm page size setup
        float mmToPoints = 72f / 25.4f;
        float pageWidth = 80f * mmToPoints;
        float pageHeight = 80f * mmToPoints;
        Document document = new Document(new Rectangle(pageWidth, pageHeight));
        document.setMargins(5f, 5f, 5f, 5f);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, outputStream);
        document.open();

        addBadgeToDocument(document, attendee);
        document.close();

        byte[] pdfBytes = outputStream.toByteArray();
        
        // Clean up
        document = null;
        outputStream.close();
        outputStream = null;
        attendee = null;
        
        return pdfBytes;
    }
} 