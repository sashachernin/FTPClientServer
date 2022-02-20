import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * this class allows each server to act as a client and to connect to other servers
 */
public class ClientNode extends Thread {

    /*array that contains objects that hold info about each server*/
    public static ArrayList<ServerObjNode> servers = new ArrayList<>();
    public static volatile boolean closeClient = false;

    public void run() {


        DataInputStream brIn = null;
        DataOutputStream pwOut = null;
        HashMap<String, SyncObj> syncMap = new HashMap<>();


        AtomicInteger numOfServers = new AtomicInteger((int) countLine("ServersList.txt"));

        String ipAddress = null;
        int portNum = 0;
        /*trying to connect*/

        /*read other servers' data from the ServersList.txt file, and try to connect to them */
        try (BufferedReader br = new BufferedReader(new FileReader("ServersList.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split(":");
                ipAddress = tokens[0];
                portNum = Integer.parseInt(tokens[1]);


                //turn ip string into address
                InetAddress add = null;
                try {
                    add = InetAddress.getByName(ipAddress);
                } catch (UnknownHostException ex) {
                    System.out.println("Illegal ip address: " + ipAddress);
                    return;
                }

                //create socket
                Socket sock = null;

                try {
                    sock = new Socket(add, portNum);

                    // connect the readers and writers
                    brIn = new DataInputStream(sock.getInputStream());
                    pwOut = new DataOutputStream(sock.getOutputStream());
                    numOfServers.getAndDecrement();
                } catch (IOException iox) {
                    // this catch statement is used for debugging

                }

                ServerObjNode srv = new ServerObjNode(sock, brIn, pwOut, portNum, add);
                servers.add(srv);

                if (srv.getSocket() != null) { //if the connection is successful
                    // this if statement was used for debugging
                    //System.out.println("successful connection on startup to one of the servers");
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*here starts the sync process with all currently connected servers*/

        try {
            //getting name of files from each server
            for (int i = 0; i < servers.size(); i++) {
                if (servers.get(i).getSocket() != null) {
                    servers.get(i).getPwOut().writeUTF("GETLIST");
                    ServerMulti.logger.info("Sent to " + servers.get(i).getSocket().getRemoteSocketAddress() + ": " + "GETLIST" + "\n");

                    String response = servers.get(i).getBrIn().readUTF();
                    ServerMulti.logger.info("Received from " + servers.get(i).getSocket().getRemoteSocketAddress() + ": " + response + "\n");

                    if (response.equals("FILELIST"))
                        System.out.println("server " + servers.get(i).getSocket().getRemoteSocketAddress() + " has no files.");
                    else {
                        String[] fileNames = (UsefulFunctions.get_path(response)).split(":");

                        for (String fileName : fileNames) {

                            syncMap.putIfAbsent(fileName, null);
                        }


                    }
                }
            }

            //getting version from each server
            for (int i = 0; i < servers.size(); i++) {
                if (servers.get(i).getSocket() != null) {
                    for (String fileName : syncMap.keySet()) {

                        servers.get(i).getPwOut().writeUTF("GETVERSION " + fileName);
                        ServerMulti.logger.info("Sent to " + servers.get(i).getSocket().getRemoteSocketAddress() + ": " + "GETVERSION " + fileName + "\n");

                        String response = servers.get(i).getBrIn().readUTF();
                        ServerMulti.logger.info("Received from " + servers.get(i).getSocket().getRemoteSocketAddress() + ": " + response + "\n");

                        if (!response.equals("ERROR")) {
                            //convert response into syncMap value
                            String[] version = response.split(" ");
                            if (syncMap.get(fileName) == null) {
                                //new file indexer value
                                syncMap.replace(fileName, new SyncObj(version[1], version[2], version[3], String.valueOf(i)));

                            } else {
                                // should UPDATE value, because of a newer timestamp
                                String old = syncMap.get(fileName).getDate();
                                String current = version[1];

                                if (UsefulFunctions.compareDateTime(old, current).equals("update")) {
                                    syncMap.put(fileName, new SyncObj(version[1], version[2], version[3], String.valueOf(i)));
                                }


                            }


                        }

                    }
                }

            }

            //sending download message to servers as part of sync
            syncMap.entrySet().forEach(entry -> {
                String serverName = entry.getValue().getLocation();
                String fileName = entry.getKey();

                if (servers.get(Integer.parseInt(serverName)).getSocket() != null) {
                    try {
                        servers.get(Integer.parseInt(serverName)).getPwOut().writeUTF("DOWNLOAD " + fileName);
                        ServerMulti.logger.info("Sent to " + servers.get(Integer.parseInt(serverName)).getSocket().getRemoteSocketAddress() + ": " + "DOWNLOAD " + fileName + "\n");

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        if (servers.get(Integer.parseInt(serverName)).getBrIn().readUTF().equals("OK")) {
                            ServerMulti.logger.info("Received from " + servers.get(Integer.parseInt(serverName)).getSocket().getRemoteSocketAddress() + ": " + "OK" + "\n");

                            //calling request for file transfer
                            UsefulFunctions.receiveFile(fileName, servers.get(Integer.parseInt(serverName)).getBrIn());

                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }


                }
            });

        } catch (IOException e) {
            System.out.println("couldn't send messages to other servers during the sync process");
            ServerMulti.logger.info("couldn't send messages to other servers during the sync process");

            e.printStackTrace();

        } catch (ParseException e) {
            System.out.println("couldn't compare datetime during the sync process for some reason");
            ServerMulti.logger.info("couldn't compare datetime during the sync process for some reason");

            e.printStackTrace();
        }
        //copy sync map to file indexer
        syncMap.forEach((key, value) -> {
            ServerMulti.fl.put(key, new FileObj(value.getDigest(), value.getDate()));
            ServerMulti.fl.get(key).setDatetime(value.getDate());
            ServerMulti.fl.get(key).setLockedby(value.getLockedby());
        });

        //update lockedby map
        ServerMulti.fl.forEach((key, value) -> {
            if (!value.getLockedby().equals("none")) {
                ServerMulti.mapOfLocks.put(key, value.getLockedby());
            }

        });


        /*sync has finished*/
        ServerMulti.syncFinished = true;
        //thread that tries to reconnect to servers that weren't connected successfully previously

        new Thread(() -> {
            while (!closeClient) {
                reconnectNullConnection(servers);
            }

        }).start();

    }


    /*function that sends OFFER message to other servers - only is the current server is the MASTER server*/
    public static boolean sendOffer(String offer) throws IOException {
        boolean areAllServersWantedToDownload = true;
        String tokens[] = offer.split(" ");
        for (ServerObjNode server : servers) {
            if (server.getSocket() != null) {
                try {
                    server.getPwOut().writeUTF(offer);
                    ServerMulti.logger.info("Sent to " + server.getSocket().getRemoteSocketAddress() + ": " + offer + "\n");

                    try {
                        String response = server.getBrIn().readUTF();
                        ServerMulti.logger.info("Received from " + server.getSocket().getRemoteSocketAddress() + ": " + response + "\n");

                        if (UsefulFunctions.get_request(response).equals("DOWNLOAD")) { // check if server responded with DOWNLOAD
                            server.getPwOut().writeUTF("OK");
                            ServerMulti.logger.info("Reply to " + server.getSocket().getRemoteSocketAddress() + ": " + "OK" + "\n");

                            // send files content
                            UsefulFunctions.sendFile(HandleClientThread.temp_name, server.pwOut);

                        } else // server replied with ERROR - doesn't want to receive the file
                            areAllServersWantedToDownload = false;
                    } catch (EOFException e) {
                        //the server was prolly closed, just ignore this catch and continue
                        ServerMulti.logger.info("Couldn't send message to " + server.getSocket().getRemoteSocketAddress() + "\n");

                    }

                } catch (SocketException e) {
                    // server is unavailable
                }
            }
        }
        return areAllServersWantedToDownload;

    }

    /*function that sends LOCK message to other servers - only is the current server is the MASTER server*/
    public static boolean sendLock(String message) throws IOException {
        boolean areAllServersWantToLock = true;
        for (ServerObjNode server : servers) {
            if (server.getSocket() != null) {
                server.getPwOut().writeUTF(message);
                ServerMulti.logger.info("Sent to " + server.getSocket().getRemoteSocketAddress() + ": " + message + "\n");
                String response = server.getBrIn().readUTF();
                ServerMulti.logger.info("Received from " + server.getSocket().getRemoteSocketAddress() + ": " + response + "\n");

                if (UsefulFunctions.get_request(response).equals("ERROR")) // server replied with ERROR - doesn't want to receive the file
                    areAllServersWantToLock = false;
            }
        }
        return areAllServersWantToLock;

    }

    /*function that sends UNLOCK message to other servers - only is the current server is the MASTER server*/
    public static void sendUnlock(String message) throws IOException {

        for (ServerObjNode server : servers) {
            if (server.getSocket() != null) {
                server.getPwOut().writeUTF(message);
                ServerMulti.logger.info("Sent to " + server.getSocket().getRemoteSocketAddress() + ": " + message + "\n");

                String response = server.getBrIn().readUTF();
                ServerMulti.logger.info("Received from " + server.getSocket().getRemoteSocketAddress() + ": " + response + "\n");

            }
        }


    }

    //function that tries to reconnect to servers that weren't connected successfully previously
    public static void reconnectNullConnection(ArrayList<ServerObjNode> servers) {

        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).getSocket() == null) { // check if the connection is null - doesn't exist
                //create socket
                Socket sock = null;
                DataInputStream brIn = null;
                DataOutputStream pwOut = null;
                try {
                    //should be replaced with ip from what the user have chosen
                    sock = new Socket(servers.get(i).getIP(), servers.get(i).getPort());

                    // connect the readers and writers
                    brIn = new DataInputStream(sock.getInputStream());
                    pwOut = new DataOutputStream(sock.getOutputStream());
                    servers.get(i).setSocket(sock, brIn, pwOut);
                    // if we got up to here - the connection was successfull


                } catch (IOException iox) {
                    // this catch statement is used of debugging

                }

            }

        }


    }

    /*function that receives files name, returns the number of lines it contains
     *usage: to know how many servers are listed in ServersList.txt file
     */
    public static long countLine(String fileName) {

        Path path = Paths.get(fileName);

        long lines = 0;
        try {


            lines = Files.lines(path).count();


        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }

    /*function that closes all connections*/
    public static void closeConnections() throws IOException {
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).getSocket() != null) {
                servers.get(i).getPwOut().writeUTF("exit");
                ServerMulti.logger.info("Sent to " + servers.get(i).getSocket().getRemoteSocketAddress() + ": " + "exit" + "\n");

                servers.get(i).getPwOut().close();
                servers.get(i).getBrIn().close();
                servers.get(i).getSocket().close();
                servers.get(i).getSocket().shutdownOutput();
                servers.get(i).getSocket().shutdownInput();

            }
        }
        closeClient = true;

    }


}