
import java.io.*;
import java.net.MalformedURLException;
import java.rmi.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.rmi.server.UnicastRemoteObject;
import org.jgroups.*;
import org.jgroups.blocks.*;
import org.jgroups.util.*;
import static org.jgroups.Message.TransientFlag.DONT_LOOPBACK;

/** Represents an front-end server than authenticates users and forwards requests to replica members. */
public class AuctionServer extends UnicastRemoteObject implements CanSell, CanBuy, CanAuthenticate {

    //--> Collection of all active auctions. Thread safe.
    private ConcurrentHashMap<String, byte[]> challengeData = new ConcurrentHashMap<>();
    private PublicKey  serverPubKey;
    private PrivateKey serverPrivKey;

    private JChannel channel;
    private RpcDispatcher dispatcher;
    private RequestOptions requestOptions;
    private RspList responses = null;

    /** AuctionServer constructor. Creates and lists remote objects inside RMIRegistry and
     *  generates the severs public and private keys. */
    public AuctionServer() throws RemoteException {
        try {
            Naming.rebind("rmi://localhost/AuctionService", this);
        } catch(RemoteException e) {
            e.printStackTrace();
            System.err.println("RemoteException caught. Reconfigure registry.");
        } catch(MalformedURLException e) {
            e.printStackTrace();
            System.err.println("URL protocol is not valid or URL constructor address is the wrong format.");
        }

        //--> Get server's public and private keys from file.
        PrivateKey serverPrivKey = null;
        PublicKey serverPublicKey = null;

        byte[] privateKeyBytes =  Utility.getFileBytes("private_key_5");
        byte[] publicKeyBytes  =  Utility.getFileBytes("public_key_5");
        PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(publicKeyBytes);

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("DSA", "SUN");
            serverPrivKey = keyFactory.generatePrivate(privKeySpec);
            serverPublicKey = keyFactory.generatePublic(pubKeySpec);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            System.err.println("No such algorithm exists for KeyFactory.");
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
            System.err.println("No such provider exists for the specified algorithm.");
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
            System.err.println("Key does not match provided specification.");
        }

        this.serverPrivKey = serverPrivKey;
        this.serverPubKey = serverPublicKey;

        try {
            this.channel = new JChannel();                                                                                    //--> Create a Channel and create request options. Block and wait for all responses. Second argument is timeout.
            this.requestOptions = new RequestOptions(ResponseMode.GET_ALL, 1000).setTransientFlags(DONT_LOOPBACK);
            this.channel.connect("AUCTION_CLUSTER");                                                               //--> Join the cluster or create if it doesn't already exist.
            this.dispatcher = new RpcDispatcher(this.channel, this);                                                 //--> Set target of remote calls (server object). Setting server object to 'this' causes infinite remote calls?
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    /** Main method calls AuctionServer constructor. Creates initial server. */
    public static void main(String[] args) {
        try {
            new AuctionServer();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /** Calls 'showAuction' method on all cluster nodes. **/
    public ArrayList<Auction> showAuction(String keyWord) throws RemoteException {

        View view = channel.getView();                                                                      //--> Get current view.
        ArrayList<Address> members = new ArrayList<Address>(view.getMembers());                                   //--> Get all members in the cluster.
        members.remove(0);                                                                            //--> Remove coordinator (RMI Server)

        //--> If no replicator servers up.
        if (members.size() == 0) {
            System.out.println("No active server to request auction data from.");
            return null;
        }

        //--> Forward closeAuction request to all cluster members.
        try {
            responses = this.dispatcher.callRemoteMethods(members,
                    "showAuction",
                    new Object[]{keyWord},
                    new Class[]{String.class},
                    this.requestOptions);
        } catch(Exception e) {
            e.printStackTrace();
        }

        HashMap<Address, Integer> responseHashes = Utility.getResponseHashValue(responses, members);        //--> Generate hash values for each of the members responses.
        Address majorityResponse = Utility.getMajorityResponseAddress(responseHashes);                      //--> Get the address of a majority response member.
        this.updateNonMajorityState(responseHashes, majorityResponse, members);                             //--> Update non-majority responses.
        return (ArrayList<Auction>) responses.getValue(majorityResponse);                                   //--> Return the response received from the majority response member.
    }

    /** Calls 'showActive' method on all cluster nodes. **/
    public ArrayList<Auction> showActive() throws RemoteException {

        View view = channel.getView();                                                                      //--> Get current view.
        ArrayList<Address> members = new ArrayList<Address>(view.getMembers());                                   //--> Get all members in the cluster.
        members.remove(0);                                                                            //--> Remove coordinator (RMI Server)

        //--> If no replicator servers up.
        if (members.size() == 0) {
            System.out.println("No active server to request auction data from.");
            return null;
        }

        //--> Forward closeAuction request to all cluster members.
        try {
            responses = this.dispatcher.callRemoteMethods(members,
                    "showActive",
                    null,
                    null,
                    this.requestOptions);
        } catch(Exception e) {
            e.printStackTrace();
        }

        if (responses == null) {
            return null;
        }

        HashMap<Address, Integer> responseHashes = Utility.getResponseHashValue(responses, members);        //--> Generate hash values for each of the members responses.
        Address majorityResponse = Utility.getMajorityResponseAddress(responseHashes);                      //--> Get the address of a majority response member.
        this.updateNonMajorityState(responseHashes, majorityResponse, members);                             //--> Update non-majority responses.
        return (ArrayList<Auction>) responses.getValue(majorityResponse);                                   //--> Return the response received from the majority response member.
    }

    /** Calls 'bid' method on all replica servers. */
    public String bid(String auctionID, UserDetails bidder, double amount) throws RemoteException {

        View view = channel.getView();                                                                      //--> Get current view.
        ArrayList<Address> members = new ArrayList<Address>(view.getMembers());                                   //--> Get all members in the cluster.
        members.remove(0);

        // If no replicator servers up.
        if (members.size() == 0) {
            System.out.println("No active server to request auction data from.");
            return null;
        }

        try {
            //--> Forward closeAuction request to all cluster members.
            responses = this.dispatcher.callRemoteMethods(members,
                    "bid",
                    new Object[]{auctionID, bidder, amount},
                    new Class[]{String.class, UserDetails.class, double.class},
                    this.requestOptions);
        } catch(Exception e) {
            e.printStackTrace();
        }

        HashMap<Address, Integer> responseHashes = new HashMap<>();

        //--> Generate response hashes.
        for (Object a : responses.keySet())  {
            String value = (String)responses.getValue(a);
            if (value == null) {
                responseHashes.put((Address)a, "null".hashCode());
            } else {
                responseHashes.put((Address)a, value.hashCode());
            }
        }

        Address majorityResponse = Utility.getMajorityResponseAddress(responseHashes);                      //--> Get the address of a majority response member.
        this.updateNonMajorityState(responseHashes, majorityResponse, members);                             //--> Update non-majority responses.
        return (String) responses.getValue(majorityResponse);                                               //--> Return the response received from the majority response member.
    }

    /** Calls 'createAuction' method on all replica servers. */
    public String createAuction(String name, double startPrice, double reserve, String desc, UserDetails userDetails) throws RemoteException {

        View view = channel.getView();                                                                      //--> Get current view.
        ArrayList<Address> members = new ArrayList<Address>(view.getMembers());                                   //--> Get all members in the cluster.
        members.remove(0);                                                                            //--> Remove coordinator (RMI Server)

        //--> If no replicator servers up.
        if (members.size() == 0) {
            System.out.println("No active server to request auction data from.");
            return null;
        }

        String auctionID = (UUID.randomUUID().toString()).substring(0, 8);

        try {
            //--> Forward closeAuction request to all cluster members.
            responses = this.dispatcher.callRemoteMethods(members,
                    "createAuction",
                    new Object[]{name, startPrice, reserve, desc, userDetails, auctionID},
                    new Class[]{String.class, double.class, double.class, String.class, UserDetails.class, String.class},
                    this.requestOptions);
        } catch(Exception e) {
            e.printStackTrace();
        }

        HashMap<Address, Integer> responseHashes = new HashMap<>();

        for (Object a : responses.keySet())  {
            String value = (String)responses.getValue(a);
            if (value == null) {
                responseHashes.put((Address)a, "null".hashCode());
            } else {
                responseHashes.put((Address)a, value.hashCode());
            }
        }

        Address majorityResponse = Utility.getMajorityResponseAddress(responseHashes);                      //--> Get the address of a majority response member.
        this.updateNonMajorityState(responseHashes, majorityResponse, members);                             //--> Update non-majority responses.
        return (String) responses.getValue(majorityResponse);                                               //--> Return the response received from the majority response member.
    }

    /** Calls 'closeAuction' method on all replica servers. */
    public Auction closeAuction(String auctionID, String username) {

        View view = channel.getView();                                                                      //--> Get current view.
        ArrayList<Address> members = new ArrayList<Address>(view.getMembers());                                   //--> Get all members in the cluster.
        members.remove(0);                                                                            //--> Remove coordinator (RMI Server)

        //--> If no replicator servers up.
        if (members.size() == 0) {
            System.out.println("No active server to request auction data from.");
            return null;
        }

        //--> Forward closeAuction request to all cluster members.
        try {
            responses = this.dispatcher.callRemoteMethods(members,
                    "closeAuction",
                    new Object[]{auctionID, username},
                    new Class[]{String.class, String.class},
                    this.requestOptions);
        } catch(Exception e) {
            e.printStackTrace();
        }

        HashMap<Address, Integer> responseHashes = new HashMap<>();

        //--> Generate hash values for auctions.
        for (Object a : responses.keySet()) {
            Auction mAuc = (Auction) responses.getValue((Address)a);
            if (mAuc != null) {
                Integer aucHashValue = mAuc.getAuctionData().hashCode();
                responseHashes.put((Address)a, aucHashValue);
            } else {
                responseHashes.put((Address)a, "null".hashCode());
            }
        }

        Address majorityResponse = Utility.getMajorityResponseAddress(responseHashes);                      //--> Get the address of a majority response member.
        this.updateNonMajorityState(responseHashes, majorityResponse, members);                             //--> Update non-majority responses.
        return (Auction) responses.getValue(majorityResponse);                                              //--> Return the response received from the majority response member.
    }

    /**
     * 1: Random set of bytes passed to server.
     * 2: Server signs challengeNumber data with its private key. Generates signature.
     * 3: Server returns signature.
     * 4: Client can verify signature using the servers public key.
     * Summary: accepts challenge from client. Returns signature.
     */
    public byte[] verifyServer(byte[] challengeNumber) {

        byte[] signature = null;

        try {
            Signature dsa = Signature.getInstance("SHA1withDSA", "SUN");			 //--> DSA signature algorithm that uses the SHA-1 message digest algorithm.
            dsa.initSign(this.serverPrivKey);                                                     //--> Initialise signature object for signing with the servers private key.
            dsa.update(challengeNumber);                                                          //--> Pass challenge value to signature object.
            signature = dsa.sign();                                                               //--> Generate signature.
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            System.err.println("No such algorithm exists for KeyFactory.");
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
            System.err.println("No such provider exists for the specified algorithm.");
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            System.err.println("Key is invalid. Check format.");
        } catch(SignatureException e) {
            e.printStackTrace();
            System.err.println("Signature format error. Check if signature format is correct.");
        }

        return signature;
    }

    /**
     * 1: Generates a set of random bytes.
     * 2: Stores random bytes with username for verification.
     * 3: Returns challenge value back to user.
     * 4: Client then signs random number with its private key. Generates signature.
     * @return
     */
    public byte[] verifyClient(String username) {

        byte[] bytes = null;

        try {
            bytes = new byte[20];
            SecureRandom.getInstanceStrong().nextBytes(bytes);
        } catch(NoSuchAlgorithmException e) {
            e.printStackTrace();
            System.err.println("No such algorithm exists for SecureRandom.");
        }

        challengeData.put(username, bytes);
        return bytes;
    }

    /**
     * 1: Client passes solution generated from the challenge value it received from the verifyClient method.
     * 2: Client passes username.
     * 3: Each username has a public key associated with it on the server side.
     * 4: Attempt the verify the signature with the public key associated with the provided username.
     * 5: Return boolean indicating if verification was successful or not.
     * @return
     */
    public boolean login(byte[] clientSignature, String username) {

        String clientPublicKeyFileName = null;

        //--> 1: Read in clients public key bytes.
        try (BufferedReader br = new BufferedReader(new FileReader("Registered-users.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {

                if (line.contains(username)) {
                    clientPublicKeyFileName= line.substring(line.indexOf(':')+1, line.indexOf('.'));
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.err.println("File path incorrect or file doesn't exist.");
        } catch(IOException e) {
            e.printStackTrace();
            System.err.println("Input / Output exception generated. Check that file name is valid.");
        }

        //--> 2: Covert client public key bytes to PublicKey object.
        PublicKey clientPublicKey = null;
        byte[] clientPubKeyBytes = Utility.getFileBytes(clientPublicKeyFileName);

        //--> If null received from getFileBytes (path doesn't exit / key doesn't exist for username provided). Return false (login failed).
        if (clientPubKeyBytes == null) {
            return false;
        }

        X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(clientPubKeyBytes);

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("DSA", "SUN");
            clientPublicKey = keyFactory.generatePublic(pubKeySpec);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            System.err.println("No such algorithm exists for KeyFactory.");
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
            System.err.println("No such provider exists for the specified algorithm.");
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
            System.err.println("Key does not match provided specification.");
        }

        //--> 3: Verify signature using clients public key.
        boolean verified = false;
        try {
            Signature sig = Signature.getInstance("SHA1withDSA", "SUN");
            sig.initVerify(clientPublicKey);
            sig.update(challengeData.get(username));
            verified = sig.verify(clientSignature);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            System.err.println("No such algorithm exists for KeyFactory.");
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
            System.err.println("No such provider exists for the specified algorithm.");
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            System.err.println("Key is invalid. Check format.");
        } catch (SignatureException e) {
            e.printStackTrace();
            System.err.println("Signature format error. Check if signature format is correct.");
        }
        return verified;
    }

    /** Updates all non-majority members with an up to date state. **/
    public void updateNonMajorityState(HashMap<Address, Integer> responseHashes, Address majorityResponse, ArrayList<Address> memberAddresses) {
        Integer majorityHash = responseHashes.get(majorityResponse);

        for (Address a : responseHashes.keySet()) {
            Integer response = responseHashes.get(a);
            if (response.equals(majorityHash) || response.equals(null)) {
                memberAddresses.remove(a);
            }
        }

        //--> Remaining addresses will be non-majority.
        if (memberAddresses.size() > 0) {
            try {
                //--> Get majority response state.
                ConcurrentHashMap<String, Auction> state = (ConcurrentHashMap<String, Auction>) this.dispatcher.callRemoteMethod(majorityResponse,
                        "getState",
                        null,
                        null,
                        this.requestOptions);
                //--> Update non majority response nodes.
                this.dispatcher.callRemoteMethods(memberAddresses,
                        "setState",
                        new Object[] {state},
                        new Class[] {ConcurrentHashMap.class},
                        this.requestOptions);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}






















