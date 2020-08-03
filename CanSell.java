
import java.rmi.*;

/** Interface used to provide the ability to create and close auctions. **/
public interface CanSell extends java.rmi.Remote {

    /** Provides the ability to create an auction. **/
    String createAuction(String name, double startPrice, double reserve, String desc, UserDetails sellerDetails) throws RemoteException;

    /** Provides the ability to close an auction. */
     Auction closeAuction(String auctionID, String username) throws RemoteException;
}