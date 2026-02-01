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

    private static final String LABEL_EVENT_TITLE = "JUG TECH DAY #5";

    private static final float BADGE_WIDTH = 80f * 2.8346457f; // 80mm in points
    private static final float BADGE_HEIGHT = 80f * 2.8346457f; // 80mm in points
    private static final float MARGIN = 5f;
    private static final float QR_SIZE = 150f;
    private static final float FONT_SIZE = 22f;
    private static final float COMPANY_FONT_SIZE = 14f;

    private static final float LABEL_WIDTH_MM = 80f;
    private static final float LABEL_HEIGHT_MM = 50f;
    private static final float LABEL_MARGIN_PT = 6f;

    // Nudge the whole 80x50 label content slightly downward (in mm).
    private static final float LABEL_TOP_SHIFT_MM = 3f;

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
                    String linkedin = columns[1] != null ? columns[1].trim() : "";
                    String company = columns[2] != null ? columns[2].trim() : "";

                    // Skip rows with missing required fields
                    if (fullName.isEmpty() || linkedin.isEmpty()) {
                        skippedRows++;
                        continue;
                    }

                    String[] nameParts = fullName.split(" ", 2);
                    String name = nameParts.length > 0 ? nameParts[0] : "";
                    String surname = nameParts.length > 1 ? nameParts[1] : "";
                    
                    attendees.add(new Attendee(name, surname, linkedin, company));
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

        String qrPayload = LinkedInNormalizer.normalizeToQrPayload(attendee.getLinkedin());
        Image qrImage = generateQRCodeImage(qrPayload);
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

        String nameSurname = attendee.getNameSurname() == null ? "" : attendee.getNameSurname().trim();
        String company = attendee.getCompany() == null ? "" : attendee.getCompany().trim();

        int nameSize;
        int nameLen = nameSurname.length();
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
        int companyLen = company.length();
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
        float contentWidth = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();

        // Title: keep it big, but ensure it fits on a single line (no wrapping like "JUG TECH\nDAY #5").
        final float headerInnerWidthPct = 92f;
        float headerFontSize = 30f;
        float titleAvailableWidth = (contentWidth * 0.62f) * (headerInnerWidthPct / 100f) - 8f; // 4pt left + 4pt right
        if (titleAvailableWidth > 0f) {
            float titleWidthAtSize = baseFont.getWidthPoint(LABEL_EVENT_TITLE, headerFontSize);
            if (titleWidthAtSize > titleAvailableWidth && titleWidthAtSize > 0f) {
                headerFontSize = headerFontSize * (titleAvailableWidth / titleWidthAtSize);
            }
        }
        headerFontSize = Math.max(12f, headerFontSize);
        Font headerFont = new Font(baseFont, headerFontSize, Font.BOLD, BaseColor.BLACK);

        float mmToPoints = 72f / 25.4f;
        // Keep the fixed title but remove the dark band for B/W printing.
        // Give the title extra vertical room to avoid clipping at larger font sizes.
        float headerHeight = 12f * mmToPoints;
        float topSpacerHeight = LABEL_TOP_SHIFT_MM * mmToPoints;
        float bodyHeight = Math.max(0f, contentHeight - headerHeight - topSpacerHeight);

        PdfPTable root = new PdfPTable(1);
        root.setWidthPercentage(100);

        PdfPCell topSpacer = new PdfPCell(new Phrase(""));
        topSpacer.setBorder(Rectangle.NO_BORDER);
        topSpacer.setFixedHeight(topSpacerHeight);
        topSpacer.setPadding(0f);
        root.addCell(topSpacer);

        // Header: align title left on the same vertical line as the name text block.
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{62f, 38f});

        PdfPCell headerLeft = new PdfPCell();
        headerLeft.setBorder(Rectangle.NO_BORDER);
        headerLeft.setPadding(0f);
        headerLeft.setFixedHeight(headerHeight);
        headerLeft.setVerticalAlignment(Element.ALIGN_MIDDLE);

        PdfPTable headerLeftInner = new PdfPTable(1);
        headerLeftInner.setWidthPercentage(headerInnerWidthPct);
        headerLeftInner.setHorizontalAlignment(Element.ALIGN_CENTER);

        PdfPCell titleCell = new PdfPCell(new Phrase(LABEL_EVENT_TITLE, headerFont));
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titleCell.setFixedHeight(headerHeight);
        titleCell.setPaddingLeft(4f);
        titleCell.setPaddingRight(4f);
        titleCell.setPaddingTop(0f);
        titleCell.setPaddingBottom(0f);
        titleCell.setNoWrap(true);
        headerLeftInner.addCell(titleCell);
        headerLeft.addElement(headerLeftInner);

        PdfPCell headerRight = new PdfPCell(new Phrase(""));
        headerRight.setBorder(Rectangle.NO_BORDER);
        headerRight.setPadding(0f);
        headerRight.setFixedHeight(headerHeight);

        header.addCell(headerLeft);
        header.addCell(headerRight);

        PdfPCell headerWrap = new PdfPCell(header);
        headerWrap.setBorder(Rectangle.NO_BORDER);
        headerWrap.setPadding(0f);
        headerWrap.setFixedHeight(headerHeight);
        root.addCell(headerWrap);

        PdfPTable body = new PdfPTable(2);
        body.setWidthPercentage(100);
        body.setWidths(new float[]{62f, 38f});

        // Left side: name + company
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPadding(0f);
        leftCell.setFixedHeight(bodyHeight);

        PdfPTable leftInner = new PdfPTable(1);
        // Keep text left-aligned, but visually center the whole text block.
        leftInner.setWidthPercentage(94f);
        leftInner.setHorizontalAlignment(Element.ALIGN_CENTER);

        PdfPCell nameCell = new PdfPCell(new Phrase(nameSurname, labelNameFont));
        nameCell.setBorder(Rectangle.NO_BORDER);
        nameCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        nameCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        nameCell.setPaddingTop(5f);
        nameCell.setPaddingLeft(6f);
        nameCell.setPaddingRight(6f);
        nameCell.setFixedHeight(bodyHeight * 0.60f);
        nameCell.setNoWrap(false);

        PdfPCell companyCell = new PdfPCell(new Phrase(company, labelCompanyFont));
        companyCell.setBorder(Rectangle.NO_BORDER);
        companyCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        companyCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        companyCell.setPaddingBottom(5f);
        companyCell.setPaddingLeft(6f);
        companyCell.setPaddingRight(6f);
        companyCell.setFixedHeight(bodyHeight * 0.40f);
        companyCell.setNoWrap(false);

        leftInner.addCell(nameCell);
        leftInner.addCell(companyCell);
        leftCell.addElement(leftInner);

        // Right side: QR code
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setPadding(0f);
        rightCell.setFixedHeight(bodyHeight);

        float rightWidth = contentWidth * 0.38f;
        float qrTarget = Math.min(rightWidth, bodyHeight);

        String qrPayload = LinkedInNormalizer.normalizeToQrPayload(attendee.getLinkedin());
        Image qrImage = generateQRCodeImage(qrPayload);
        qrImage.scaleToFit(qrTarget, qrTarget);

        PdfPCell qrCell = new PdfPCell(qrImage, true);
        qrCell.setBorder(Rectangle.NO_BORDER);
        qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        qrCell.setFixedHeight(bodyHeight);
        qrCell.setPaddingTop(2f);
        qrCell.setPaddingBottom(2f);

        PdfPTable rightInner = new PdfPTable(1);
        rightInner.setWidthPercentage(100);
        rightInner.addCell(qrCell);
        rightCell.addElement(rightInner);

        body.addCell(leftCell);
        body.addCell(rightCell);

        PdfPCell bodyCell = new PdfPCell(body);
        bodyCell.setBorder(Rectangle.NO_BORDER);
        bodyCell.setPadding(0f);
        bodyCell.setFixedHeight(bodyHeight);
        root.addCell(bodyCell);

        document.add(root);
    }

    private String generateVCard(Attendee attendee) {
        return "BEGIN:VCARD\n" +
                "VERSION:3.0\n" +
                "N:" + attendee.getSurname() + ";" + attendee.getName() + ";;;\n" +
                "FN:" + attendee.getNameSurname() + "\n" +
                "ORG:" + attendee.getCompany() + "\n" +
                "EMAIL:" + attendee.getLinkedin() + "\n" +
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
        if (LinkedInNormalizer.normalizeToQrPayload(attendee.getLinkedin()).isEmpty()) {
            throw new IllegalArgumentException("LinkedIn (or Email) is required");
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
        if (LinkedInNormalizer.normalizeToQrPayload(attendee.getLinkedin()).isEmpty()) {
            throw new IllegalArgumentException("LinkedIn (or Email) is required");
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