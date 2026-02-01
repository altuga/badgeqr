package jug.org.qr;

public class Attendee {
    private final String name;
    private final String surname;
    private final String company;
    private final String linkedin;

    public Attendee(String name, String surname, String linkedin, String company) {
        this.name = name;
        this.surname = surname;
        this.company = company;
        this.linkedin = linkedin;
    }

    public String getName() { return name; }
    public String getSurname() { return surname; }
    public String getCompany() { return company; }

    public String getLinkedin() { return linkedin; }
    public String getEmail() { return linkedin; }

    public String getNameSurname() { return name + " " + surname; }
}