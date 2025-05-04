package jug.org.qr;

public class Attendee {
    private final String name;
    private final String surname;
    private final String company;
    private final String email;

    public Attendee(String name, String surname, String email, String company) {
        this.name = name;
        this.surname = surname;
        this.company = company;
        this.email = email;
    }

    public String getName() { return name; }
    public String getSurname() { return surname; }
    public String getCompany() { return company; }
    public String getEmail() { return email; }

    public String getNameSurname() { return name + " " + surname; }
}