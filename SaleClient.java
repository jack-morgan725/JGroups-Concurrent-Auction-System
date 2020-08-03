
import java.net.MalformedURLException;
import java.rmi.*;

/** Creates a client that can access the auction system, create new auctions, and close auctions. */
public class SaleClient extends Client {

    /** Logs in seller and provides them with a seller interface to create and close auctions. **/
    public static void main(String[] args) {

        while(true) {

            UserDetails sellerDetails = Client.authenticateAndLogin();

            if (sellerDetails == null) {
                System.out.println("Authentication failed. Please check that you've entered the correct username.");
            }

            //--> Main loop. Allow user to select an operation.
            while (sellerDetails != null) {
                System.out.printf("Buyer Options: %n>> Create Auction (1).%n>> End Auction (2).%n>> Exit (3).%n");
                System.out.println("------------------------------------------------------------->");
                System.out.print("Select Option: ");
                String operation = validator.getNoneEmptyStringInput("Operation");

                switch(operation) {
                    case "1": { SaleClient.createAuction(sellerDetails); break; }
                    case "2": { SaleClient.removeAuction(sellerDetails.getName()); break; }
                    case "3": { System.exit(0); }
                    default: break;
                }
            }
        }
    }

    /** Provides client-side interface to create and auction. **/
    public static void createAuction(UserDetails sellerDetails) {

        //--> Allow user to input a name for their auction.
        System.out.print(">> Auction Name: ");
        String name = validator.getNoneEmptyStringInput("Auction name");

        //--> Allow user to input a start price for their auction. Must be > 0 and a double.
        System.out.printf(">> Auction Start Price: %s", "\u00A3");
        double startPrice = validator.getValidPrice("Start price");

        //--> Get reserve price. While reserve price is less than or equal start price.
        System.out.println("------------------------------------------------------------->");
        System.out.printf(">> Auction Reserve Price: %s","\u00A3");
        double reserve = 0;

        //--> If reserve less than or equal start price -> print out error message.
        while (reserve <= startPrice) {
            reserve = validator.getValidPrice("Reserve");
            if (reserve <= startPrice) {
                System.out.println("------------------------------------------------------------->");
                System.out.printf(">> Reserve price must be greater than start price. Please enter %n>> a valid reserve price greater than %s%.2f: %s", "\u00A3", startPrice, "\u00A3");
            }
        }

        //--> Get description.
        System.out.println("------------------------------------------------------------->");
        System.out.printf(">> Auction Description: ");
        String desc = validator.getNoneEmptyStringInput("Description");
        String auctionID;

        CanSell seller = SaleClient.getAuctionServer();
        try {
            auctionID = seller.createAuction(name, startPrice, reserve, desc, sellerDetails);

            if (auctionID == null) {
                System.out.println(">> Server down. Please try again later.");
                System.out.println("------------------------------------------------------------->");
                return;
            }

            System.out.printf(">> Auction created. Unique auction ID: %s%n", auctionID);
            System.out.println("------------------------------------------------------------->");
        } catch (RemoteException e) {
            e.printStackTrace();
            System.out.println("RemoteException caught. Reconfigure registry.");
        }
    }

    /** Obtains a reference to the remote buyer object. **/
    public static CanSell getAuctionServer() {

        CanSell seller = null;

        try {
            seller = (CanSell) Naming.lookup("rmi://localhost/AuctionService");
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
        return seller;
    }

    /** Provides client-side interface to remove an auction and prints out auction winner details if appropriate. **/
    public static void removeAuction(String username) {

        //--> Allow user to input the name of an auction to remove.
        System.out.print(">> Auction ID: ");
        String auctionName = validator.getNoneEmptyStringInput("Auction ID");
        Auction auction = null;

        //--> Retrieve reference to remote seller object and the target auction.
        CanSell seller = SaleClient.getAuctionServer();
        try {
            auction = seller.closeAuction(auctionName, username);
        } catch (RemoteException e) {
            e.printStackTrace();
            System.out.println("RemoteException caught. Reconfigure registry.");
        }

        //--> If auction doesn't exist or wrong name entered -> print error message and return.
        if (auction == null) {
            System.out.printf(">> You do not have permission to close this auction. You can only close auctions that you have created.%n" +
                              "If you're seeing this error but are sure you're entering the correct ID then the server is down. Please%n" +
                              "try again later.%n");
            System.out.println("------------------------------------------------------------->");
            return;
        }

        //--> If top bid is greater than reserve -> print winner details.
        if (auction.getTopBid() >= auction.getReserve()) {

            UserDetails winner = auction.getTopBidder();
            System.out.println(">> Auction reserve was met. Buyer details: ");
            System.out.println(">> Winner Name: " + winner.getName());
            System.out.println(">> Winner Email: " + winner.getEmail());
            System.out.println("------------------------------------------------------------->");
            return;
        //--> Else print reserve not met message.
        } else {
            System.out.println(">> Auction reserve was not met.");
            System.out.println("------------------------------------------------------------->");
            return;
        }
    }
}