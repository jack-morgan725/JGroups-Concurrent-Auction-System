
import org.jgroups.Address;
import org.jgroups.util.RspList;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Utility {

    /** Returns the address of a member that's response was in the majority. */
    public static Address getMajorityResponseAddress(HashMap<Address, Integer> hashPairs) {

        //--> 1: Keep track of occurrence of hash values. Key is hash value. Value is occurrence.
        HashMap<Integer, Integer> occurrenceTracker = new HashMap<Integer, Integer>();

        //--> 2: Get all response hash values.
        ArrayList<Integer> hashValues = new ArrayList<Integer>(hashPairs.values());

        //--> 3: Set key of the occurrence HashMap to be hash value and occurrence rate to 0.
        for (int i = 0; i < hashValues.size(); i++) {
            occurrenceTracker.put(hashValues.get(i), 0);
        }

        //--> 4: Loop through all hash values and update occurrence rate each time a hash value appears.
        for (int i = 0; i < hashValues.size(); i++) {
            Integer hashValue = hashValues.get(i);
            Set<Integer> hashKeys = occurrenceTracker.keySet();
            Integer occurrence = occurrenceTracker.get(hashValue);
            occurrenceTracker.put(hashValue, occurrence += 1);
        }

        //--> 5: Find the greatest number of times a hash appeared.
        int highestOccurrence   = 0;
        int mostCommonHashValue = 0;
        for (Integer occurrence : occurrenceTracker.values()) {
            if (occurrence >= highestOccurrence) {
                highestOccurrence = occurrence;
            }
        }

        //--> 6: Get the hash that appeared the highest number of times.
        for (Integer hash : occurrenceTracker.keySet()) {
            if (occurrenceTracker.get(hash).equals(highestOccurrence)) {
                mostCommonHashValue = hash;
            }
        }

        //--> 7: Get the address of a member that returned the most common hash value.
        for (Address memberAddress : hashPairs.keySet()) {
            int hashValue = hashPairs.get(memberAddress);
            if (hashValue == mostCommonHashValue) {
                return memberAddress;
            }
        }
        return null;
    }

    /** Generates the hash values for a response that contains multiple auctions. */
    public static HashMap<Address, Integer> getResponseHashValue(RspList memberResponses, ArrayList<Address> memberAddresses) {

        HashMap<Address, Integer> hashPairs = new HashMap<Address, Integer>();

        //--> Loop through every member of the cluster.
        for (int i = 0; i < memberAddresses.size(); i++) {

            //--> Create a massive string of all auction data for that member.
            String auctionString = "";

            //--> Get all auction objects that were returned by the cluster member.
            ArrayList<Auction> memberAuctions = (ArrayList<Auction>) memberResponses.getValue(memberAddresses.get(i));

            //--> If response was null. Save that instead of a hash value.
            if (memberAuctions == null) {
                hashPairs.put(memberAddresses.get(i), null);
                continue;
            }

            String auctionData = "";

            //--> Loop through every auction for this cluster member and appends all auction data to a string.
            for (int j = 0; j < memberAuctions.size(); j++) {
                Auction auction = memberAuctions.get(j);
                auctionData += auction.getAuctionData();
            }

            //--> Store hash value of massive String for this member in HashMap using the cluster members address as a key.
            hashPairs.put(memberAddresses.get(i), auctionString.hashCode());
        }

        //--> Return the hash values for the responses from each member of the cluster in a <Member Address, Response Hash Value> pair.
        return hashPairs;
    }

    /** Reads a file and returns its contents in the form of a byte array. */
    public static byte[] getFileBytes(String filePath) {

        byte[] fileBytes = null;

        try {
            FileInputStream iStream = new FileInputStream(filePath);                            //--> Create input stream for file.
            fileBytes = new byte[iStream.available()];                                          //--> Create array size of file.
            iStream.read(fileBytes);                                                            //-->  Move file contents to byte array.
            iStream.close();
        } catch (FileNotFoundException e) {
            //--> If file not found -> return null.
            return null;
        } catch(IOException e) {
            e.printStackTrace();
            System.err.println("Input / Output exception generated. Check that file name is valid.");
        } catch (NullPointerException e) {
            return null;
        }

        return fileBytes;
    }

    /** Returns the hash values of all the states returned in a response and the address associated with that response. */
    public static HashMap<Address, Integer> getHashMapHashes(RspList responses) {

        HashMap <Address, Integer> hashAddresses = new HashMap<>();

        for (Object a: responses.keySet()) {

            ConcurrentHashMap<String, Auction> tempResponse = (ConcurrentHashMap<String, Auction>) responses.getValue((Address)a);
            HashMap<String, Auction> memberResponse = new HashMap<String, Auction>(tempResponse);

            // Make hash value for object from keys.
            String hashString = "";
            for (String s : memberResponse.keySet()) {
                hashString += s;
            }

            for (Auction auc : memberResponse.values()) {
                hashString += auc.getAuctionData();
            }

            hashAddresses.put((Address)a, hashString.hashCode());
        }

        return hashAddresses;
    }
}
