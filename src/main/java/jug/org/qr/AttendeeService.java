package jug.org.qr;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AttendeeService {


    public static List<Attendee> readAttendeesFromCSV(String csvPath) throws Exception {
        List<Attendee> attendees = new ArrayList<>();

        // Open the CSV file using UTF-8 encoding
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath, StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;

            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false; // Skip header
                    continue;
                }

                // Split by comma, ensuring empty fields are kept
                String[] columns = line.split(",", -1);

                if (columns.length >= 3) {
                    String fullName = columns[0].trim();   // Ad Soyad
                    String linkedin = columns[1].trim();      // LinkedIn handle/URL
                    String company = columns[2].trim();    // Şirket

                    // Split full name into name and surname (best effort)
                    String[] nameParts = fullName.split(" ", 2);
                    String name = nameParts.length > 0 ? nameParts[0] : "";
                    String surname = nameParts.length > 1 ? nameParts[1] : "";
                    System.out.println("Name: " + fullName + ", LinkedIn: " + linkedin + ", Company: " + company);
                    attendees.add(new Attendee(name, surname, linkedin, company));
                }
            }
        }

        return attendees;
    }
}