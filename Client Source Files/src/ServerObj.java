import java.io.*;
import java.net.InetAddress;
import java.net.Socket;


public class ServerObj
{

    InetAddress add;
    int portNum;
    Socket sock;
    DataInputStream brIn;
    DataOutputStream pwOut;
    String name;

    //constructor
    ServerObj(Socket sock, DataInputStream brIn, DataOutputStream pwOut, InetAddress add, int portNum, String name )
    {

        this.sock=sock;
        this.brIn=brIn;
        this.pwOut=pwOut;
        this.add=add;
        this.portNum=portNum;
        this.name=name;

    }

    //getters
    public Socket getSocket() { return sock; }

    public DataInputStream getBrIn() { return brIn; }

    public DataOutputStream getPwOut() { return pwOut; }

    public InetAddress getAdd() { return add; }

    public int getPort() { return portNum; }

    public String getName() { return name; }

    //setters
    public void setSocket(Socket sock,DataInputStream brIn, DataOutputStream pwOut) {
        this.sock=sock;
        this.brIn=brIn;
        this.pwOut=pwOut;
    }




}
