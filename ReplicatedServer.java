
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.View;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.util.RspList;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/** Maintains a consistent view of the auction data. */
public class ReplicatedServer {

    private ConcurrentHashMap<String, Auction> auctions = new ConcurrentHashMap<>();
    private JChannel channel;
    private RpcDispatcher dispatcher;

    /**
     * Closes the auction associated with the provided auctionID providing that the auction was created
     * by the user specified by the provided username.
     */
    public Auction closeAuction(String auctionID, String username) {

        System.out.println(">> Replica closing auction.");
        System.out.println("------------------------------------------------------------->");

        //--> Remove auction and return the removed Auction object so that the winner information can be printed client-side.
        Auction auction = auctions.get(auctionID);
        if (auction == null) {
            return null;
        } else if (username.equals(auction.getSellerDetails().getName())) {
            return auctions.remove(auctionID);
        } else {
            //--> If null returned, print out error message on client side.
            return null;
        }
    }

    /** Allows user to bid on an item. **/
    public String bid(String auctionID, UserDetails bidder, double amount) throws RemoteException {

        System.out.println(">> Replica updating bid value and top bidder.");
        System.out.println("------------------------------------------------------------->");
        Auction targetAuction = auctions.get(auctionID);

        //--> If auction doesn't exist -> Return error message for client to print.
        if (targetAuction == null) {
            return "1";
        }

        //--> If auction exists and bid is greater than current top bid -> set user as new top bidder and update top bid.
        if (amount > targetAuction.getTopBid()) {
            targetAuction.setTopBid(amount);
            targetAuction.setTopBidder(bidder);
            return "2";
        }

        //--> Else let the client know that the bid was rejected as it is lower than the current active bid.
        return "3";
    }

    /** Creates a new auction and adds it to the AuctionServer. **/
    public String createAuction(String name, double startPrice, double reserve, String desc, UserDetails userDetails, String auctionID) throws RemoteException {

        //--> Create and add new auction to AuctionServer.
        Auction auction = new Auction(name, startPrice, reserve, desc, userDetails, auctionID);
        auctions.put(auction.getAuctionID(), auction);

        //--> Server-side confirmation that auction was created.
        System.out.println(">> Replica created auction. Total active auction count: " + auctions.size() + ".");
        System.out.println("------------------------------------------------------------->");

        //--> Return name (ID) of newly created auction.
        return auction.getAuctionID();
    }

    /** Returns a list of all active auctions. These auctions then be displayed client side. **/
    public ArrayList<Auction> showActive() throws RemoteException {

        System.out.println(">> Replica returning all active auctions.");
        System.out.println("------------------------------------------------------------->");

        //--> Retrieve all active auctions from AuctionServer.
        ConcurrentHashMap<String, Auction> auctions = this.auctions;
        ArrayList<Auction> allAuctions = new ArrayList<>(auctions.values());

        //--> Return all active auctions.
        return allAuctions;
    }

    /** Returns a list of all active auctions that contain the supplied keyWord. **/
    public ArrayList<Auction> showAuction(String keyWord) throws RemoteException {

        System.out.println(">> Replica returning all active auctions that match search criteria.");
        System.out.println("------------------------------------------------------------->");

        //--> Retrieve all active auctions from AuctionServer.
        ConcurrentHashMap<String, Auction> auctions = this.auctions;
        ArrayList<Auction> allAuctions = new ArrayList<Auction>(auctions.values());
        ArrayList<Auction> filteredAuctions = new ArrayList<Auction>();

        //--> Filter auctions by keyword.
        for (int i = 0; i < allAuctions.size(); i++) {
            if (((allAuctions.get(i)).getName()).contains(keyWord)) {
                filteredAuctions.add(allAuctions.get(i));
            }
        }

        //--> Return list of auctions that meet the search criteria.
        return filteredAuctions;
    }

    public void start() throws Exception {

        this.channel = new JChannel();
        this.channel.connect("AUCTION_CLUSTER");
        RequestOptions requestOptions = new RequestOptions(ResponseMode.GET_ALL, 1000);
        this.dispatcher = new RpcDispatcher(this.channel, this);	                                  //--> Set target for remote calls. Acts as server (receiver).

        View view = channel.getView();                                                                      //--> Get current view.
        ArrayList<Address> members = new ArrayList<Address>(view.getMembers());                                   //--> Get all members in the cluster.
        members.remove(0);                                                                            //--> Remove coordinator (RMI Server)

        //--> If other members exist in the cluster -> go get the state from them.
        if (members.size() != 0) {
            //--> Get state of all cluster nodes (don't send request to RMI server).
            RspList responses = dispatcher.callRemoteMethods(members, "getState", null, null, requestOptions);

            //--> Get the address of a member who's response was in the majority.
            Address majoritySender = Utility.getMajorityResponseAddress(Utility.getHashMapHashes(responses));

            //--> Get the actual response associated with the majority sender address.
            ConcurrentHashMap<String, Auction> cHash = (ConcurrentHashMap<String, Auction>) responses.getValue(majoritySender);

            //--> Update the state with the majority response state.
            synchronized (auctions) {
                auctions.clear();
                auctions.putAll(cHash);
            }
        }

        System.out.println(">> State retrieved from cluster member. Total active auction count: " + auctions.size());
        System.out.println("------------------------------------------------------------->");
    }

    /** Creates and initialises replica server. **/
    public static void main(String args[]) throws Exception{
        new ReplicatedServer().start();
    }

    /** Called remotely by new members of the group to get an up to date state. **/
    public ConcurrentHashMap<String, Auction> getState() {
        return auctions;
    }

    /** Called remotely by RMI server when a response is not as expected. **/
    public void setState(ConcurrentHashMap<String, Auction> auctions) {
        synchronized (auctions) {
            this.auctions.clear();
            this.auctions.putAll(auctions);
        }
    }
}