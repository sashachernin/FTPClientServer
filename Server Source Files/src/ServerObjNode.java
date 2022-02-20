import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

/*
 * This class is used to store the sockets of each server that is specified in the ServersList.txt file.
 * Objects of this class are stored in an array. This way makes it easier to manage the connected servers.
 * Example of use: iterating over the array that contains objects of this class, and sending OFFER message to all connections.
 */

public class ServerObjNode {


    int portNum;
    InetAddress ip;
    Socket sock;
    DataInputStream brIn;
    DataOutputStream pwOut;


    //constructor
    ServerObjNode(Socket sock, DataInputStream brIn, DataOutputStream pwOut, int portNum, InetAddress ip) {

        this.sock = sock;
        this.brIn = brIn;
        this.pwOut = pwOut;

        this.portNum = portNum;
        this.ip = ip;

    }

    //getters
    public Socket getSocket() {
        return sock;
    }

    public DataInputStream getBrIn() {
        return brIn;
    }

    public DataOutputStream getPwOut() {
        return pwOut;
    }

    public int getPort() {
        return portNum;
    }

    public InetAddress getIP() {
        return ip;
    }


    //setters
    public void setSocket(Socket sock, DataInputStream brIn, DataOutputStream pwOut) {
        this.sock = sock;
        this.brIn = brIn;
        this.pwOut = pwOut;
    }


}