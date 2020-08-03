
import java.io.Serializable;

/** Represents a buyer or seller member of the auction system. */
public class UserDetails implements Serializable {

    private String name;
    private String email;

    /** User constructor method. */
    public UserDetails(String name, String email) {
        this.name = name;
        this.email = email;
    }

    /** Returns the users name. */
    public String getName() {
        return name;
    }

    /** Returns the users email address. */
    public String getEmail() {
        return email;
    }
}