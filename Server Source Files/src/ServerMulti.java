import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Enumeration;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


public class ServerMulti {
    public static volatile boolean syncFinished = false;
    public static String storagePath;
    public static int port;

    //concurrent hash map of locked files - file name as a key, the unique name of the locking client as a value
    public static ConcurrentHashMap<String, String> mapOfLocks = new ConcurrentHashMap<>();
    //file indexer - file name as a key, FileObj as a value (contains datetime, digest, lockedby)
    public static ConcurrentHashMap<String, FileObj> fl = new ConcurrentHashMap<>();
    //clients indexer - RemoteSocketAddress as a key, and its unique name that is received from the client as a value
    public static ConcurrentHashMap<SocketAddress, String> clientsIndexer = new ConcurrentHashMap<>();

    //logger object
    public static Logger logger = Logger.getLogger("MyLog");


    public static void main(String[] args) {
        FileHandler fh;
        System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tF %1$tT] %5$s %n");
        try {

            // This block configure the logger with handler and formatter
            fh = new FileHandler("./MyLogFile.log");
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
            logger.setUseParentHandlers(false);

            // the following statement is used to log any messages
            logger.info("Server is up");

        } catch (SecurityException | IOException e) {
            e.printStackTrace();
            System.out.println("couldn't set up log file");
        }


        Path path = Paths.get("config.txt");

        //get storage path from config file
        try {
            storagePath = Files.readAllLines(Paths.get(String.valueOf(path.toRealPath()))).get(0);
            port = Integer.parseInt(Files.readAllLines(Paths.get(String.valueOf(path.toRealPath()))).get(1));
        } catch (IOException e) {
            System.out.println("couldn't read config file");
            e.printStackTrace();
        }
        InetAddress address = selectIPAddress();

        System.out.println("Starting sync of files...");

        //Starting the "client" side of this server, so it could connect to other servers
        ClientNode client = new ClientNode();
        client.start();

        /*check if sync finished */

        while (!syncFinished) {
            Thread.onSpinWait();
        }


        System.out.println("Startup sync has completed");
        logger.info("Startup sync has completed");

        String line = "";
        Listener listener = null;
        BufferedReader brIn = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            // open a socket on the port
            ServerSocket serverSock = null;
            try {
                serverSock = new ServerSocket(port, 30, address);
                listener = new Listener(serverSock);
                listener.start();
            } catch (IOException e) {
                logger.warning("Couldn't create socket");
                e.printStackTrace();
            }

            System.out.println(
                    "Started to listen.  Enter \"stop\" to pause listening: "
            );

            try {
                do {
                    line = brIn.readLine();
                } while (!line.equalsIgnoreCase("stop"));

                // user asked to stop
                listener.interrupt();
                serverSock.close();
                System.out.println("Stopped listening.  To quit, enter \"quit\".  To resume listening, enter \"resume\": ");

                do {
                    line = brIn.readLine();
                } while (!line.equalsIgnoreCase("quit") && !line.equalsIgnoreCase("resume"));

                if (line.equals("resume")) {
                    logger.info("Continues listening...");
                    continue;
                } else if (line.equals("quit")) {
                    ClientNode.closeConnections(); // close all the connections to other servers
                    break;

                }
            } catch (IOException e) {
                // this shouldn't happen, just quit
                listener.interrupt();
                break;
            }
        }
        System.out.println("Goodbye.");
        System.exit(0); // this call is safe since all sockets are closed, both on server and client sides of this app.
        return;
    }

    /**
     * Gets the user to select an IP address to listen on from the list of local ones.
     *
     * @return The selected IP address
     */
    public static InetAddress selectIPAddress() {
        // get the local IPs
        Vector<InetAddress> addresses = getLocalIPs();
        // see how many they are

        System.out.println("Choose an IP address to listen on:");
        for (int i = 0; i < addresses.size(); i++) {
            // show it in the list
            System.out.println(i + ": " + addresses.elementAt(i).toString());
        }

        BufferedReader brIn = new BufferedReader(new InputStreamReader(System.in));
        int choice = -1;

        while (choice < 0 || choice >= addresses.size()) {
            System.out.print(": ");
            try {
                String line = brIn.readLine();
                choice = Integer.parseInt(line.trim());
            } catch (Exception ex) {
                System.out.print("Error parsing choice\n: ");
                logger.warning("couldn't read IPv4 of this PC");
            }
        }

        return addresses.elementAt(choice);

    }

    public static Vector<InetAddress> getLocalIPs() {
        // make a list of addresses to choose from
        // add in the usual ones
        Vector<InetAddress> adds = new Vector<InetAddress>();
        try {
            adds.add(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}));
            adds.add(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
        } catch (UnknownHostException ex) {
            // something is really weird - this should never fail
            System.out.println("Can't find IP address 0.0.0.0: " + ex.getMessage());
            logger.warning("Can't find IP address 0.0.0.0: " + ex.getMessage());
            ex.printStackTrace();
            return adds;
        }

        try {
            // get the local IP addresses from the network interface listing
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                // see if it has an IPv4 address
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    // go over the addresses and add them
                    InetAddress add = addresses.nextElement();
                    // make sure it's an IPv4 address
                    if (!add.isLoopbackAddress() && add.getClass() == Inet4Address.class) {
                        adds.addElement(add);
                    }
                }
            }
        } catch (SocketException ex) {
            // can't get local addresses, something's wrong
            System.out.println("Can't get network interface information: " + ex.getLocalizedMessage());
            logger.warning("Can't get network interface information: " + ex.getLocalizedMessage());
        }
        return adds;
    }

}
