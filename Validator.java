
import java.util.Scanner;

/** Validator class used to validate user input. */
public class Validator {

    /** Checks if a String starts with a space or is empty. Returns validated user input. */
    public String getNoneEmptyStringInput(String fieldName) {

        Scanner scanner = new Scanner(System.in);
        String userInput;

        while (true) {
            userInput = scanner.nextLine();
            System.out.println("------------------------------------------------------------->");

            if (userInput.isEmpty()) {
                System.out.printf(">> %s field empty. Please enter a valid %s: ", fieldName, fieldName.toLowerCase());
                continue;
            } else if (userInput.startsWith(" ")){
                System.out.printf(">> %s field cannot start with an empty space. Please enter a valid %s: ", fieldName, fieldName.toLowerCase());
                continue;
            } else {
                return userInput;
            }
        }
    }

    /** Returns a valid price entered by the user. Valid means that it is a number and is not negative. */
    public double getValidPrice(String fieldName) {

        Scanner scanner = new Scanner(System.in);
        double value = 0;

        while(value <= 0) {

            while (!scanner.hasNextDouble()) {
                System.out.printf(">> Please enter a valid %s. %s must be a number.%n", fieldName.toLowerCase(), fieldName);
                System.out.println("------------------------------------------------------------->");
                System.out.printf(">> %s: %s", fieldName, "\u00A3");
                scanner.next();
            }

            value = Math.round(scanner.nextDouble() * 100.0) / 100.0;
            scanner.nextLine();

            if (value < 0) {
                System.out.printf(">> %s must be greater than zero. Please enter a valid %s. %n", fieldName, fieldName.toLowerCase());
                System.out.println("------------------------------------------------------------->");
                System.out.printf(">> %s: %s", fieldName, "\u00A3");
            }
        }
        return value;
    }

    /** Returns a valid email address input by the user. */
    public String getValidEmail() {
        String userInput;
        while(true) {

            //--> Get user input.
            userInput = this.getNoneEmptyStringInput("Email");

            //--> Check if supplied address is at least minimum length.
            if (userInput.length() <= 4) {
                System.out.printf(">> Please enter a valid email address: ");
                continue;
            }

            /**
             * Check if first part of String contains character '@' after initial character and before the second to
             * last character AND Check if DOT located after initial character that comes after "@" symbol but before
             * the last character in the String.
             */
            if ((userInput.substring(1, userInput.length() - 2).contains("@")) && (userInput.substring(userInput.lastIndexOf("@") + 2, userInput.length() - 1)).contains("."))
                break;
            else
                System.out.printf(">> Please enter a valid email address: ");
        }
        return userInput;
    }
}
