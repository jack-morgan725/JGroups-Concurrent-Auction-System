
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.*;

/** Superclass of BuyClient and SaleClient. Provides shared functionality such as the ability to login. */
public abstract class Client implements Serializable {

    protected static Validator validator = new Validator();

    /**
     * 1: Gets clients username and email address.
     * 2: Reads in client private and public keys files and server public key file.
     * 3: Authenticates server using challenge-response protocol.
     * 4: Authenticates client with server using challenge response protocol
     * 5: Returns user details (username and email address) if login was successful or not.
     */
    public static UserDetails authenticateAndLogin() {

        boolean serverVerified = false;
        boolean clientVerified = false;

        //--> Get user username, password, and email address.
        System.out.println("------------------------------------------------------------->");
        System.out.print  (">> Enter your username: ");
        String username = validator.getNoneEmptyStringInput("Username");

        System.out.print  (">> Enter your email address: ");
        String email = validator.getValidEmail();

        UserDetails userDetails = new UserDetails(username, email);

        //--> Get reference to clients public and private keys and the servers public key.
        PrivateKey myPrivateKey = null;
        PublicKey myPublicKey = null;
        PublicKey serverPublicKey = null;

        /**
         * Change to public / private key one (JackMorgan).
         * Change to public / private key two (CryptoMan).
         * Change to public / private key three (AuctionMaster).
         * Assumes client only has a single key at any given time and that they're already registered.
         */
        byte[] myPubKeyBytes = Client.getFileBytes("public_key_3");
        byte[] myPrivKeyBytes = Client.getFileBytes("private_key_3");
        byte[] serverPubKeyBytes = Client.getFileBytes("public_key_5");

        X509EncodedKeySpec myPubSpec = new X509EncodedKeySpec(myPubKeyBytes);
        PKCS8EncodedKeySpec myPrivSpec = new PKCS8EncodedKeySpec(myPrivKeyBytes);
        X509EncodedKeySpec serverPubSpec = new X509EncodedKeySpec(serverPubKeyBytes);

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("DSA", "SUN");
            myPublicKey = keyFactory.generatePublic(myPubSpec);
            myPrivateKey = keyFactory.generatePrivate(myPrivSpec);
            serverPublicKey = keyFactory.generatePublic(serverPubSpec);
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

        //--> 1: Get reference to remote AuctionServer.
        CanAuthenticate clientAuth = (CanAuthenticate)BuyClient.getAuctionServer();

        //--> 2: Challenge server authenticity. Server will create signature from challenge value using its private key.
        byte[] serverSolution = null;

        //--> 3: Generate a random challenge value that won't be used again (set of 20 random bytes).
        byte[] challengeData = Client.generateChallengeValue();

        //--> 4: Send challenge value to server and get signature back.
        try {
            serverSolution = clientAuth.verifyServer(challengeData);
        } catch (RemoteException e) {
            e.printStackTrace();
            System.err.println("RemoteException caught. Reconfigure registry.");
        }
        //--> 5: Verify that the signature is correct using the servers public key (public key 5) and the challenge value initially passed.
        try {
            Signature sig = Signature.getInstance("SHA1withDSA", "SUN");                     //--> Must use same algorithm that was used to generate the signature.
            sig.initVerify(serverPublicKey);                                                                  //--> Set signature to verify using server public key.
            sig.update(challengeData);                                                                        //--> Pass challenge data that it needs to check signature against.
            serverVerified = sig.verify(serverSolution);                                                      //--> Pass signature bytes and check if it matches the data.
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

        //--> Authenticating client with server.
        try {
            //--> 1: Get challenge value from server.
            byte[] clientChallengeValue = clientAuth.verifyClient(username);

            //--> 2: Client sign challenge value and generate signature using client private key.
            Signature dsa = Signature.getInstance("SHA1withDSA", "SUN");
            dsa.initSign(myPrivateKey);                                                       //--> Initialise signature object to sign with private key.
            dsa.update(clientChallengeValue);                                                 //--> Insert received challenge data to sign.
            byte[] signature = dsa.sign();                                                    //--> Generate signature.

            //--> 3: Send signature to server for verification. Server should check it using clients public key.
            clientVerified = clientAuth.login(signature, username);
        } catch(RemoteException e) {
            e.printStackTrace();
            System.out.println("RemoteException caught. Reconfigure registry.");
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

        System.out.println(">> Server Verified: " + serverVerified + ". Client Verified: " + clientVerified);
        System.out.println("------------------------------------------------------------->");
        if (serverVerified && clientVerified) {
            return userDetails;
        } else {
            return null;
        }
    }

    /** Generates random byte array used as challenge value. */
    public static byte[] generateChallengeValue() {

        byte[] bytes = null;

        try {
            bytes = new byte[20];
            SecureRandom.getInstanceStrong().nextBytes(bytes);
        } catch(NoSuchAlgorithmException e) {
            e.printStackTrace();
            System.err.println("No such algorithm exists for SecureRandom.");
        }
        return bytes;
    }

    /** Reads a file and returns its contents in the form of a byte array. */
    public static byte[] getFileBytes(String filePath) {

        byte[] fileBytes = null;
        try {
            FileInputStream iStream = new FileInputStream(filePath);    //--> Create input stream for file.
            fileBytes = new byte[iStream.available()];                  //--> Create array size of file.
            iStream.read(fileBytes);                                    //--> Move file contents to byte array.
            iStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.err.println("File path incorrect or file doesn't exist.");
        } catch(IOException e) {
            e.printStackTrace();
            System.err.println("Input / Output exception generated. Check that file name is valid.");
        }

        return fileBytes;
    }
}