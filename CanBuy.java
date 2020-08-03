
import java.rmi.*;
import java.util.ArrayList;

/** Interface used to provide the ability to bid and view auctions. **/
public interface CanBuy extends java.rmi.Remote {

    /** Provides ability to bid on items. **/
    String bid(String auctionName, UserDetails bidder, double amount) throws RemoteException;

    /** Displays all active auctions to the buyer. **/
    ArrayList<Auction> showActive() throws RemoteException;

    /** Search functionality. Displays all auctions that contain the supplied keyword.
     * Returns ArrayList of all auctions which can then be output on the client side. **/
    ArrayList<Auction> showAuction(String keyWord) throws RemoteException;
}