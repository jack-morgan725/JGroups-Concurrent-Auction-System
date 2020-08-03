
import java.rmi.RemoteException;

/** Interface used to provide the ability to authenticate and login. **/
public interface CanAuthenticate extends java.rmi.Remote {

    /** Sends a challenge to the server and returns a signature. **/
    byte[] verifyServer(byte[] challengeNumber) throws RemoteException;

    /** Returns a challenge value to the client. **/
    byte[] verifyClient(String username) throws RemoteException;

    /** Verifies the clients signature (challenge response) with the key associated with the provided username. **/
    boolean login(byte[] challengeResponse, String username) throws RemoteException;
}
