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

    private static final float LABEL_WIDTH_MM = 80f;
    private static final float LABEL_HEIGHT_MM = 50f;
    private static final float LABEL_MARGIN_PT = 6f;

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

        for (int i = 0; i < attendees.size(); i++) {
            addBadgeToDocument(document, attendees.get(i));
            if (i < attendees.size() - 1) {
                document.newPage();
            }
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

    public byte[] generateLabels80x50(MultipartFile file) throws Exception {
        List<Attendee> attendees = readAttendeesFromCSV(file);
        if (attendees.isEmpty()) {
            throw new IllegalArgumentException("No valid attendees found in CSV");
        }

        float mmToPoints = 72f / 25.4f;
        float pageWidth = LABEL_WIDTH_MM * mmToPoints;
        float pageHeight = LABEL_HEIGHT_MM * mmToPoints;

        Document document = new Document(new Rectangle(pageWidth, pageHeight));
        document.setMargins(LABEL_MARGIN_PT, LABEL_MARGIN_PT, LABEL_MARGIN_PT, LABEL_MARGIN_PT);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, outputStream);
        document.open();

        for (int i = 0; i < attendees.size(); i++) {
            addLabel80x50ToDocument(document, attendees.get(i));
            if (i < attendees.size() - 1) {
                document.newPage();
            }
        }

        document.close();
        byte[] pdfBytes = outputStream.toByteArray();
        outputStream.close();
        attendees.clear();
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
        BaseFont baseFont = nameFont.getBaseFont();

        String nameSurname = attendee.getNameSurname() == null ? "" : attendee.getNameSurname().trim();
        String company = attendee.getCompany() == null ? "" : attendee.getCompany().trim();

        int nameLen = nameSurname.length();
        int nameFontSize;
        if (nameLen > 28) {
            nameFontSize = 14;
        } else if (nameLen > 20) {
            nameFontSize = 16;
        } else if (nameLen > 14) {
            nameFontSize = 18;
        } else {
            nameFontSize = 22;
        }

        int companyLen = company.length();
        int companyFontSize;
        if (companyLen > 34) {
            companyFontSize = 10;
        } else if (companyLen > 24) {
            companyFontSize = 12;
        } else {
            companyFontSize = 14;
        }

        Font localNameFont = new Font(baseFont, nameFontSize, Font.BOLD);
        Font localCompanyFont = new Font(baseFont, companyFontSize);

        float contentHeight = document.getPageSize().getHeight() - document.topMargin() - document.bottomMargin();
        float contentWidth = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();

        // Use a fixed-height table so name/company never push the QR onto a new page.
        PdfPTable layout = new PdfPTable(1);
        layout.setWidthPercentage(100);

        PdfPCell nameCell = new PdfPCell(new Phrase(nameSurname, localNameFont));
        nameCell.setBorder(Rectangle.NO_BORDER);
        nameCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        nameCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        nameCell.setFixedHeight(contentHeight * 0.22f);
        nameCell.setPaddingTop(2f);
        nameCell.setPaddingBottom(2f);
        nameCell.setNoWrap(false);

        PdfPCell companyCell = new PdfPCell(new Phrase(company, localCompanyFont));
        companyCell.setBorder(Rectangle.NO_BORDER);
        companyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        companyCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        companyCell.setFixedHeight(contentHeight * 0.16f);
        companyCell.setPaddingTop(0f);
        companyCell.setPaddingBottom(2f);
        companyCell.setNoWrap(false);

        String vCardData = generateVCard(attendee);
        Image qrImage = generateQRCodeImage(vCardData);
        float qrTarget = Math.min(contentWidth, contentHeight * 0.62f);
        qrImage.scaleToFit(qrTarget, qrTarget);

        PdfPCell qrCell = new PdfPCell(qrImage, true);
        qrCell.setBorder(Rectangle.NO_BORDER);
        qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        qrCell.setFixedHeight(contentHeight * 0.62f);
        qrCell.setPaddingTop(2f);
        qrCell.setPaddingBottom(2f);

        layout.addCell(nameCell);
        layout.addCell(companyCell);
        layout.addCell(qrCell);

        document.add(layout);
    }

    private void addLabel80x50ToDocument(Document document, Attendee attendee) throws Exception {
        BaseFont baseFont = nameFont.getBaseFont();

        int nameSize;
        int nameLen = attendee.getNameSurname() != null ? attendee.getNameSurname().trim().length() : 0;
        // 80x50 has limited width; prefer smaller sizes early to avoid clipping.
        if (nameLen > 34) {
            nameSize = 10;
        } else if (nameLen > 28) {
            nameSize = 11;
        } else if (nameLen > 22) {
            nameSize = 12;
        } else if (nameLen > 18) {
            nameSize = 14;
        } else if (nameLen > 14) {
            nameSize = 15;
        } else {
            nameSize = 16;
        }

        int companySize;
        int companyLen = attendee.getCompany() != null ? attendee.getCompany().trim().length() : 0;
        if (companyLen > 34) {
            companySize = 8;
        } else if (companyLen > 26) {
            companySize = 9;
        } else if (companyLen > 18) {
            companySize = 10;
        } else {
            companySize = 11;
        }

        Font labelNameFont = new Font(baseFont, nameSize, Font.BOLD);
        Font labelCompanyFont = new Font(baseFont, companySize);

        float contentHeight = document.getPageSize().getHeight() - document.topMargin() - document.bottomMargin();

        PdfPTable outer = new PdfPTable(2);
        outer.setWidthPercentage(100);
        outer.setWidths(new float[]{60f, 40f});

        // Left side: name (top-left) + company (bottom-left)
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPadding(0f);
        leftCell.setFixedHeight(contentHeight);

        PdfPTable leftInner = new PdfPTable(1);
        leftInner.setWidthPercentage(100);

        PdfPCell nameCell = new PdfPCell(new Phrase(attendee.getNameSurname(), labelNameFont));
        nameCell.setBorder(Rectangle.NO_BORDER);
        nameCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        // Keep name from hugging the very top.
        nameCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        nameCell.setPaddingTop(6f);
        nameCell.setPaddingRight(6f);
        nameCell.setFixedHeight(contentHeight * 0.58f);
        nameCell.setNoWrap(false);

        PdfPCell companyCell = new PdfPCell(new Phrase(attendee.getCompany() == null ? "" : attendee.getCompany(), labelCompanyFont));
        companyCell.setBorder(Rectangle.NO_BORDER);
        companyCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        // Pull company upward a bit so it doesn't sit at the very bottom.
        companyCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        companyCell.setPaddingBottom(6f);
        companyCell.setPaddingRight(6f);
        companyCell.setFixedHeight(contentHeight * 0.42f);
        companyCell.setNoWrap(false);

        leftInner.addCell(nameCell);
        leftInner.addCell(companyCell);
        leftCell.addElement(leftInner);

        // Right side: QR code (top-right) + email (below)
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setPadding(0f);
        rightCell.setFixedHeight(contentHeight);

        PdfPTable rightInner = new PdfPTable(1);
        rightInner.setWidthPercentage(100);

        Image qrImage = generateQRCodeImage(attendee.getEmail() == null ? "" : attendee.getEmail().trim());
        // Scale QR to fit nicely within the right column.
        float qrTarget = contentHeight * 0.92f;
        qrImage.scaleToFit(qrTarget, qrTarget);

        PdfPCell qrCell = new PdfPCell(qrImage, true);
        qrCell.setBorder(Rectangle.NO_BORDER);
        qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        qrCell.setPaddingTop(2f);
        qrCell.setPaddingBottom(2f);
        qrCell.setFixedHeight(contentHeight);

        rightInner.addCell(qrCell);
        rightCell.addElement(rightInner);

        outer.addCell(leftCell);
        outer.addCell(rightCell);

        document.add(outer);
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

    public byte[] generateSingleLabel80x50(Attendee attendee) throws Exception {
        if (attendee == null) {
            throw new IllegalArgumentException("Attendee data is required");
        }
        if (attendee.getName() == null || attendee.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (attendee.getEmail() == null || attendee.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }

        float mmToPoints = 72f / 25.4f;
        float pageWidth = LABEL_WIDTH_MM * mmToPoints;
        float pageHeight = LABEL_HEIGHT_MM * mmToPoints;
        Document document = new Document(new Rectangle(pageWidth, pageHeight));
        document.setMargins(LABEL_MARGIN_PT, LABEL_MARGIN_PT, LABEL_MARGIN_PT, LABEL_MARGIN_PT);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, outputStream);
        document.open();

        addLabel80x50ToDocument(document, attendee);
        document.close();

        byte[] pdfBytes = outputStream.toByteArray();
        outputStream.close();
        return pdfBytes;
    }
} 