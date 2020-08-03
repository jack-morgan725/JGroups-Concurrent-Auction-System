
import java.io.Serializable;

/** Represents an auction maintained by the AuctionServer. */
public class Auction implements Serializable {

    private double startPrice;
    private double reserve;
    private double topBid;
    private String name;
    private String desc;
    private String auctionID;
    private UserDetails topBidder;
    private UserDetails sellerDetails;

    /** Construction for Auction objects. */
    public Auction(String name, double startPrice, double reserve, String desc, UserDetails sellerDetails, String auctionID) {
        this.name = name;
        this.startPrice = startPrice;
        this.reserve = reserve;
        this.desc = desc;
        this.sellerDetails = sellerDetails;

        //--> TopBid set to start price so bids lower than start price aren't accepted.
        this.topBid = startPrice;

        //--> Get substring of UUID. Limit auction ID to 8 characters.
        this.auctionID = auctionID;
    }

    /** Returns the auctions name. **/
    public String getAuctionName() { return name; }

    /** Returns the auctions current top bidder.  **/
    public UserDetails getTopBidder() { return topBidder; }

    /** Returns the auctions current top bid. **/
    public double getTopBid() { return topBid; }

    /** Returns the auctions reserve price. **/
    public double getReserve() { return reserve; }

    /** Returns the auctions description. **/
    public String getAuctionDesc() { return desc; }

    /** Returns the auctions name. **/
    public String getName() { return name; }

    /** Updates the auctions top bid. **/
    public void setTopBid(double topBid) { this.topBid = topBid; }

    /** Updates the auctions top bidder. **/
    public void setTopBidder(UserDetails topBidder) { this.topBidder = topBidder; }

    /** Returns the auctions unique ID. **/
    public String getAuctionID() { return auctionID; }

    /** Returns the sellers details (name and email address) **/
    public UserDetails getSellerDetails() { return sellerDetails; }

    /** Returns the auction starting price. **/
    public double getStartPrice() { return startPrice; }

    /** Returns a String of all auction data. **/
    public String getAuctionData() {
        String auctionString  = "";
        String topBidderName  = "";
        String topBidderEmail = "";

        if (topBidder != null) {
            topBidderName = topBidder.getName();
            topBidderEmail = topBidder.getEmail();
        }

        String sellerName  = sellerDetails.getName();
        String sellerEmail = sellerDetails.getEmail();

        auctionString = auctionString + startPrice + reserve + topBid + name + desc + auctionID + sellerName + sellerEmail + topBidderName + topBidderEmail;
        return auctionString;
    }

}