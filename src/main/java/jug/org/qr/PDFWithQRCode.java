package jug.org.qr;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jug.org.qr.AttendeeService.readAttendeesFromCSV;

public class PDFWithQRCode {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Please provide the path to the CSV file as a command-line argument.");
            System.exit(1);
        }

        String csvFile = args[0]; // CSV file from command line

        try {
            List<Attendee> attendees = readAttendeesFromCSV(csvFile);

            new File("badges").mkdir(); // Create output folder if not exist

            // 80mm x 80mm page size setup
            float mmToPoints = 72f / 25.4f;
            float pageWidth = 80f * mmToPoints;
            float pageHeight = 80f * mmToPoints;
            Document document = new Document(new Rectangle(pageWidth, pageHeight));

            // Get current date and time in the format YYYY-MM-DD_HH-MM-SS
            String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            // Set margins (adjust as needed)
            document.setMargins(5f, 5f, 5f, 5f);  // Left, Right, Top, Bottom in points
            // Use current date-time for the output PDF filename
            String outputPdfPath = "badges/AllBadges_" + currentDateTime + ".pdf";


            PdfWriter.getInstance(document, new FileOutputStream(outputPdfPath));
            document.open();

            for (int i = 0; i < attendees.size(); i++) {
                addBadgeToDocument(document, attendees.get(i));
                if (i < attendees.size() - 1) {
                    document.newPage(); // New page between badges
                }
            }

            document.close();
            System.out.println("Generated combined badge file: " + outputPdfPath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Create a badge for each attendee

    public static void addBadgeToDocument(Document document, Attendee attendee) throws Exception {
        // Load DejaVuSans font from the resources folder as InputStream
        InputStream fontStream = PDFWithQRCode.class.getClassLoader().getResourceAsStream("dejavu-fonts-ttf-2.37/ttf/DejaVuSans.ttf");

        if (fontStream == null) {
            throw new Exception("Font file not found in resources!");
        }

        // Copy the font to a temporary file
        File tempFontFile = File.createTempFile("DejaVuSans", ".ttf");
        tempFontFile.deleteOnExit(); // Make sure the temp file is deleted when the JVM exits
        try (FileOutputStream out = new FileOutputStream(tempFontFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fontStream.read(buffer)) != -1) {
                out.write(buffer, 0, length);
            }
        }

        // Create BaseFont from the temporary TTF file
        BaseFont baseFont = BaseFont.createFont(tempFontFile.getAbsolutePath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);

        // Adjust font size based on name length
        int nameFontSize = (attendee.getNameSurname().length() > 12) ? 16 : 22; // Smaller size for long names

        Font nameFont = new Font(baseFont, nameFontSize, Font.BOLD);

        // Add Name - 17pt Bold (Using DejaVuSans font that supports Turkish characters)
        Paragraph nameParagraph = new Paragraph(attendee.getNameSurname(), nameFont);
        nameParagraph.setAlignment(Element.ALIGN_CENTER);
        nameParagraph.setSpacingAfter(1f); // Reduced space after name
        document.add(nameParagraph);

        // Add Company - 14pt Regular (Using the same font for consistency)
        Font companyFont = new Font(baseFont, 14);
        Paragraph companyParagraph = new Paragraph(attendee.getCompany(), companyFont);
        companyParagraph.setAlignment(Element.ALIGN_CENTER);
        companyParagraph.setSpacingAfter(2f); // Reduced space after company
        document.add(companyParagraph);

        // Generate QR Code
        String qrPayload = LinkedInNormalizer.normalizeToQrPayload(attendee.getLinkedin());
        Image qrImage = generateQRCodeImage(qrPayload);

        // Scale QR code to 100px for better fit
        int qrPixelSize = 150; // Reduced for better fit
        qrImage.scaleToFit(qrPixelSize, qrPixelSize);
        qrImage.setAlignment(Element.ALIGN_CENTER); // Center QR code
        document.add(qrImage);

        // Ensure no additional space is added to the page
        document.add(new Paragraph("")); // Empty space to balance layout
    }

    // Generate QR code image
    public static Image generateQRCodeImage(String data) throws WriterException, IOException, BadElementException {
        int qrPixelSize = 300; // High enough for quality
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        BitMatrix bitMatrix = new MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, qrPixelSize, qrPixelSize, hints);
        BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", baos);
        byte[] imageBytes = baos.toByteArray();

        return Image.getInstance(imageBytes);
    }
}