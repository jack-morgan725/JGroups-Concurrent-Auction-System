
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.*;
import java.security.*;
import java.util.ArrayList;

/** Creates a client that can access the auction system, view auctions, and bid on auctions.*/
public class BuyClient extends Client {

    /** Logs in buyer and provides them with a buyer interface to bid and view auctions. **/
    public static void main(String[] args) {

        while(true) {

            UserDetails userDetails = Client.authenticateAndLogin();

            if (userDetails == null) {
                System.out.println("Authentication failed. Please check that you've entered the correct username.");
            }

            //--> Main loop. Allow user to select an operation. If userDetails == null. Authentication has failed.
            while (userDetails != null) {
                System.out.printf("Buyer Options: %n>> Bid (1).%n>> Show auctions (2).%n>> Search for auction (3).%n>> Exit (4).%n");
                System.out.println("------------------------------------------------------------->");
                System.out.print("Select Option: ");
                String operation = validator.getNoneEmptyStringInput("Operation");

                switch(operation) {
                    case "1": { BuyClient.bid(userDetails); break; }
                    case "2": { BuyClient.viewAuctions(); break; }
                    case "3": { BuyClient.showAuctions(); break; }
                    case "4": { System.exit(0); }
                    default: break;
                }
            }
        }
    }

    /** Provides client side buyer interface for bidding. **/
    public static void bid(UserDetails buyerDetails) {

        //--> Retrieve the ID of the auction the client wishes to bid on.
        System.out.printf(">> Auction ID: ");
        String aucName = validator.getNoneEmptyStringInput("Auction ID");

        //--> Get the bid amount from the user.
        System.out.printf(">> Bid Amount: %s", "\u00A3");
        double amount = validator.getValidPrice("Bid");

        //--> Retrieve the remote buyer object, bid on the target auction, and print the results of the bid.
        CanBuy buyer = BuyClient.getAuctionServer();
        try {

            String bidString = buyer.bid(aucName, buyerDetails, amount);

            if (bidString == null) {
                System.out.println(">> Server down. Please try again later.");
                System.out.println("------------------------------------------------------------->");
                return;
            } else {
                switch(bidString) {
                    case "1": { System.out.println(">> Bid rejected. Auction doesn't exist."); break;}
                    case "2": { System.out.println(">> Bid accepted. You're the highest bidder."); break; }
                    case "3": { System.out.println(">> Bid rejected. Current top bid exceeds offered amount."); break;}
                }
            }
            System.out.println("------------------------------------------------------------->");
        } catch (RemoteException e) {
            e.printStackTrace();
            System.err.println("RemoteException caught. Reconfigure registry.");
        }
    }

    /** Obtains a reference to the remote buyer object. */
    public static CanBuy getAuctionServer() {

        CanBuy buyer = null;

        try {
            buyer = (CanBuy) Naming.lookup("rmi://localhost/AuctionService");
        } catch (RemoteException e) {
            e.printStackTrace();
            System.out.println("RemoteException caught. Reconfigure registry.");
        } catch(NotBoundException e) {
            e.printStackTrace();
            System.out.println("Registry lookup name has no associated binding. Check if specified service name is correct.");
        } catch(MalformedURLException e) {
            e.printStackTrace();
            System.out.println("URL protocol is not valid or URL constructor address is the wrong format.");
        }
        return buyer;
    }

    /** Prints a list of all active auctions to the client. */
    public static void viewAuctions() {

        ArrayList<Auction> auctions = new ArrayList<>();

        //--> Retrieve the remote buyer object and retrieve a list of all active auctions.
        CanBuy buyer = BuyClient.getAuctionServer();
        try {
            auctions = buyer.showActive();
        } catch (RemoteException e) {
            e.printStackTrace();
            System.out.println("RemoteException caught. Reconfigure registry.");
        }

        if (auctions == null) {
            System.out.println(">> Server down. Please try again later.");
            System.out.println("------------------------------------------------------------->");
            return;
        }

        //--> Loop through list of active auctions and print them out client-side.
        BuyClient.printAuctionList(auctions);
    }

    /** Provides search functionality for buyer. Buyer can search for auctions using a supplied keyword. */
    public static void showAuctions() {

        ArrayList<Auction> auctions = new ArrayList<>();

        //--> Allow user to enter a keyword to refine what auctions are displayed.
        System.out.printf(">> Search: ");
        String keyWord = validator.getNoneEmptyStringInput("Search");

        //--> Retrieve reference to remote buyer object and all active auctions that meet the provided search criteria.
        CanBuy buyer = BuyClient.getAuctionServer();
        try {
            auctions = buyer.showAuction(keyWord);
        } catch (RemoteException e) {
            e.printStackTrace();
            System.out.println("RemoteException caught. Reconfigure registry.");
        }

        if (auctions == null) {
            System.out.println(">> Server down. Please try again later.");
            System.out.println("------------------------------------------------------------->");
            return;
        }

        //--> Loop through and print all retrieved auctions to the client.
        BuyClient.printAuctionList(auctions);
    }

    /** Prints out a list of supplied auction objects in a human readable format. */
    public static void printAuctionList(ArrayList<Auction> auctions) {

        //--> If no auctions met search criteria.
        if (auctions == null) {
            System.out.println(">> Server down. Please try again later.");
            System.out.println("------------------------------------------------------------->");
            return;
        }

        if (auctions.size() == 0) {
            System.out.println(">> No auctions were found.");
            System.out.println("------------------------------------------------------------->");
        }

        for (int i = 0; i < auctions.size(); i++) {

            Auction tAuction = auctions.get(i);

            System.out.println(">> Name: " + tAuction.getAuctionName());
            System.out.println(">> Unique Auction ID: " + tAuction.getAuctionID());
            System.out.printf(">> Top Bid: %s%.2f%n", "\u00A3" , tAuction.getTopBid());
            System.out.println(">> Description: " + tAuction.getAuctionDesc());
            System.out.println("------------------------------------------------------------->");
        }
    }

    /** Generates a set of public and private keys. */
    public static void keyGen() {

        PrivateKey privateKey = null;
        PublicKey publicKey   = null;

        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA", "SUN");
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
            keyGen.initialize(1024, random);

            KeyPair pair = keyGen.generateKeyPair();
            privateKey = pair.getPrivate();
            publicKey = pair.getPublic();

        } catch(NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        }

        try {
            byte[] encodedPublicKey = publicKey.getEncoded();
            FileOutputStream pubKeyFos = new FileOutputStream("public_key_name");
            pubKeyFos.write(encodedPublicKey);
            pubKeyFos.close();

            byte[] encodedPrivateKey = privateKey.getEncoded();
            FileOutputStream privKeyFos = new FileOutputStream("private_key_name");
            privKeyFos.write(encodedPrivateKey);
            privKeyFos.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        } catch(NullPointerException e) {
            e.printStackTrace();
        }
    }
}