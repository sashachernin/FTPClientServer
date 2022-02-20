import java.io.*;

import java.net.Socket;

import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class HandleClientThread extends Thread {

    private Socket clientSock;


    /**
     * Initializes the server.
     *
     * @param sock The client socket to handle.
     */
    public HandleClientThread(Socket sock) {
        super("HandleClientThread-" + sock.getRemoteSocketAddress().toString());
        this.clientSock = sock;
    }

    public static String temp_name;

    /**
     * Runs the server
     */
    public void run() {

        ServerMulti.logger.info("Connected: " + clientSock.getInetAddress() + " " + clientSock.getLocalPort() + "\n");

        //generating a random string that will be used as temporary name for new files, until we make sure that we can add them to the file indexer
        temp_name = UsefulFunctions.GeneratingRandomAlphabetic();

        // listen for a connection
        try (
                // attach a buffered reader and print writer
                DataInputStream brIn = new DataInputStream(clientSock.getInputStream());
                DataOutputStream pw = new DataOutputStream(clientSock.getOutputStream())) {
            while (!isInterrupted()) {

                // now listen for a sentence

                String message = null;
                try {
                    message = brIn.readUTF();
                } catch (SocketException e) {
                    ServerMulti.logger.warning("Something went wrong with the connection " + clientSock.getLocalAddress() + ":" + clientSock.getLocalPort());
                    break;
                } catch (EOFException e) {
                    // if this exception was caught, it is prolly because the client wanted to know if this server is online
                    break;
                }


                ServerMulti.logger.info("Received from " + clientSock.getRemoteSocketAddress() + ":" + message + "\n");


                //file to upload request
                if (UsefulFunctions.get_request(message).equals("UPLOAD") && !isInterrupted()) {
                    String filename = UsefulFunctions.get_path(message); //store files name
                    String datetime = UsefulFunctions.getDateTime(); //store datetime of NOW
                    String clientName = ServerMulti.clientsIndexer.get(clientSock.getRemoteSocketAddress()); //get the name of the client that sent the request

                    UsefulFunctions.receiveFile(temp_name, brIn);// store the files content with a temporary name

                    FileObj response = ServerMulti.fl.putIfAbsent(filename, new FileObj("temp", datetime)); //trying to add the file to the file indexer
                    if (response == null) { //the file was added successfully to the indexer

                        String offer = "OFFER " + UsefulFunctions.sha256Fixed(temp_name) + " " + datetime + " " + filename + " " + clientName;


                        if (ClientNode.sendOffer(offer)) { // send OFFER to other servers, if all servers that received the OFFER wanted to download, do all this
                            Files.move(Paths.get(ServerMulti.storagePath + temp_name), Paths.get(ServerMulti.storagePath + filename), REPLACE_EXISTING);
                            ServerMulti.fl.replace(filename, new FileObj(UsefulFunctions.sha256Fixed(filename), datetime));
                            //upload finished
                            pw.writeUTF("OK");
                            ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "OK" + "\n");
                        } else { // at least one server replied with ERROR to the OFFER
                            ServerMulti.fl.remove(filename);
                            pw.writeUTF("ERROR");
                            ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "ERROR" + "\n");
                        }
                    } else { // this file already exist in the file indexer. wasn't added successfully
                        if (ServerMulti.mapOfLocks.containsKey(filename) && ServerMulti.mapOfLocks.get(filename).equals(ServerMulti.clientsIndexer.get(clientSock.getRemoteSocketAddress()))) { // check if this file is locked by the requesting client

                            String offer = "OFFER " + UsefulFunctions.sha256Fixed(temp_name) + " " + datetime + " " + filename + " " + clientName;

                            if (ClientNode.sendOffer(offer)) { // send OFFER to other servers, if all servers that received the OFFER wanted to download, do all this
                                Path temp = Files.move(Paths.get(ServerMulti.storagePath + temp_name), Paths.get(ServerMulti.storagePath + filename), REPLACE_EXISTING);
                                ServerMulti.fl.replace(filename, new FileObj(UsefulFunctions.sha256Fixed(filename), datetime));
                                ServerMulti.fl.get(filename).setLockedby(clientName);
                                //update finished
                                pw.writeUTF("OK");
                                ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "OK" + "\n");
                            } else { // at least one server replied with ERROR to the OFFER
                                pw.writeUTF("ERROR");
                                ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "ERROR" + "\n");
                            }


                        } else { //file is locked by someone else, automatically send ERROR without sending OFFER to others


                            pw.writeUTF("ERROR");
                            ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "ERROR" + "\n");

                        }


                    }
                    //delete temp file if for some reason it was left over
                    File temp = new File(ServerMulti.storagePath + temp_name);
                    temp.delete();
                }
                //file to download request
                if (UsefulFunctions.get_request(message).equals("DOWNLOAD") && !isInterrupted()) {
                    String filename = UsefulFunctions.get_path(message); //get file's name
                    if (ServerMulti.fl.containsKey(filename)) { // check if the file exist in file indexer
                        pw.writeUTF("OK");
                        ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "OK" + "\n");
                        UsefulFunctions.sendFile(filename, pw);
                    } else {
                        pw.writeUTF("ERROR");
                        ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "ERROR" + "\n");

                    }

                }

                //get version of file request
                if (UsefulFunctions.get_request(message).equals("GETVERSION") && !isInterrupted()) {
                    String filename = UsefulFunctions.get_path(message); //get file's name

                    if (ServerMulti.fl.containsKey(filename)) { // check if the file exist in file indexer
                        FileObj file = ServerMulti.fl.get(filename); // retrieve files data from the file indexer
                        pw.writeUTF("VERSION " + file.datetime + " " + file.hash + " " + file.getLockedby());
                        ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "VERSION " + file.datetime + " " + file.hash + "\n");

                    } else {
                        pw.writeUTF("ERROR");
                        ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "ERROR" + "\n");

                    }


                }

                //get list of files request
                if (UsefulFunctions.get_request(message).equals("GETLIST") && !isInterrupted()) {

                    pw.writeUTF(UsefulFunctions.getFilesList(ServerMulti.fl)); // get list of files from the file indexer, and send to the requesting client
                    ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + UsefulFunctions.getFilesList(ServerMulti.fl) + "\n");

                }

                //lock file
                if (UsefulFunctions.get_request(message).equals("LOCK") && !isInterrupted()) {

                    String[] tokens = message.split(" ");
                    String filename = tokens[1];
                    String clientName = tokens[2];


                    if (ServerMulti.clientsIndexer.containsKey(clientSock.getRemoteSocketAddress())) { //check if the lock request is received from one of the clients (and NOT from one of the other servers )

                        if (ServerMulti.fl.containsKey(filename) && !ServerMulti.mapOfLocks.containsKey(filename)) { // check if file exists and not locked

                            String response = ServerMulti.mapOfLocks.putIfAbsent(filename, ServerMulti.clientsIndexer.get(clientSock.getRemoteSocketAddress())); // try to put the files name in the locked files indexer

                            if (response == null) { // if the files name was added to the locked files indexer successfully,


                                if (ClientNode.sendLock("LOCK " + filename + " " + ServerMulti.clientsIndexer.get(clientSock.getRemoteSocketAddress()))) { //send lock request to other servers, and check if all returned "ok"
                                    pw.writeUTF("OK");
                                    ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "OK" + "\n");
                                    ServerMulti.fl.get(filename).setLockedby(ServerMulti.clientsIndexer.get(clientSock.getRemoteSocketAddress()));
                                } else { //someone returned ERROR, unlock on this server and others
                                    pw.writeUTF("ERROR");
                                    ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "ERROR" + "\n");

                                    ServerMulti.fl.get(filename).setLockedby("none");
                                    ServerMulti.mapOfLocks.remove(filename);
                                    ClientNode.sendUnlock("UNLOCK " + filename + " " + ServerMulti.clientsIndexer.get(clientSock.getRemoteSocketAddress()));

                                }
                            } else { // other client already locked this file, don't even send lock request to others

                                pw.writeUTF("ERROR");
                                ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "ERROR" + "\n");
                            }


                        } else { //file already locked on this server, don't even send lock to others

                            pw.writeUTF("ERROR");
                            ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "ERROR" + "\n");

                        }


                    } else { //request sent by some other server, and NOT by a client
                        if (ServerMulti.fl.containsKey(filename) && !ServerMulti.mapOfLocks.containsKey(filename)) { // check if such file exist and not locked

                            if (ServerMulti.mapOfLocks.putIfAbsent(filename, clientName) == null) { // trying to put the files name in the locked files' indexer
                                // file added to the locked files' indexer successfully
                                ServerMulti.fl.get(filename).setLockedby(clientName);
                                pw.writeUTF("OK");
                                ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "OK" + "\n");
                            } else { // couldn't add file to the locked files indexer - means someone locked it already
                                pw.writeUTF("ERROR");
                                ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "OK" + "\n");

                            }

                        } else { // file doesn't exist or it is already locked
                            pw.writeUTF("ERROR");
                            ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "ERROR" + "\n");

                        }


                    }


                }

                //unlock file
                if (UsefulFunctions.get_request(message).equals("UNLOCK") && !isInterrupted()) {
                    String[] tokens = message.split(" ");
                    String filename = tokens[1]; // get files name
                    String clientname = tokens[2]; // get clients name and sent the request to unlock


                    if (ServerMulti.clientsIndexer.containsKey(clientSock.getRemoteSocketAddress())) { // check if the request is sent by a client or by a server
                        /*client sent the unlock message*/
                        if (ServerMulti.fl.containsKey(filename) && ServerMulti.mapOfLocks.containsKey(filename) && ServerMulti.mapOfLocks.get(filename).equals(ServerMulti.clientsIndexer.get(clientSock.getRemoteSocketAddress()))) {
                            /*file exist and is locked by requesting client, so we can unlock it*/
                            ServerMulti.mapOfLocks.remove(filename); // remove it from client indexer
                            ServerMulti.fl.get(filename).setLockedby("none"); // set "lockedby" field to "none"
                            /*send unlock to others*/
                            ClientNode.sendUnlock("UNLOCK " + filename + " " + ServerMulti.clientsIndexer.get(clientSock.getRemoteSocketAddress()));
                            pw.writeUTF("OK");
                            ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "OK" + "\n");

                        } else {
                            pw.writeUTF("ERROR");
                            ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "ERROR" + "\n");

                        }
                    } else {
                        /*server sent the unlock message*/
                        if (ServerMulti.fl.containsKey(filename) && ServerMulti.mapOfLocks.containsKey(filename) && ServerMulti.mapOfLocks.get(filename).equals(clientname)) {
                            /*file exist and is locked by requesting client, so we can unlock it*/
                            ServerMulti.mapOfLocks.remove(filename); // remove it from client indexer
                            ServerMulti.fl.get(filename).setLockedby("none"); // set "lockedby" field to "none"
                            pw.writeUTF("OK");
                            ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "OK" + "\n");

                        } else {
                            /*file doesn't exist, locked by someone else or not locked at all*/
                            pw.writeUTF("ERROR");
                            ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "ERROR" + "\n");

                        }

                    }
                }

                //OFFER request
                if (UsefulFunctions.get_request(message).equals("OFFER") && !isInterrupted()) {
                    String[] tokens = message.split(" ");
                    String digest = tokens[1];
                    String datetime = tokens[2];
                    String filename = tokens[3];
                    String clientName = tokens[4];

                    //check if I can receive this file, by trying to add it to the file indexer
                    FileObj response = ServerMulti.fl.putIfAbsent(filename, new FileObj("temp", datetime));
                    if (response == null) {
                        /*new file that doesn't exist on this server, download it*/
                        ServerMulti.fl.replace(filename, new FileObj(digest, datetime));
                        /*get files content by sending the "DOWNLOAD" message*/

                        try {
                            pw.writeUTF("DOWNLOAD " + filename);
                            ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "DOWNLOAD " + filename + "\n");

                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        if (brIn.readUTF().equals("OK")) {
                            ServerMulti.logger.info("Received from " + clientSock.getRemoteSocketAddress() + ": " + "OK" + "\n");
                            UsefulFunctions.receiveFile(filename, brIn);
                        }

                    } else { // the file already exist in the file indexer

                        // check if this file is locked by the requesting client
                        if (ServerMulti.mapOfLocks.containsKey(filename) && ServerMulti.mapOfLocks.get(filename).equals(clientName)) {
                            //file is indeed locked by the requesting client
                            ServerMulti.fl.replace(filename, new FileObj(digest, datetime));
                            ServerMulti.fl.get(filename).setLockedby(clientName);
                            //get files content
                            try {
                                pw.writeUTF("DOWNLOAD " + filename);
                                ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "DOWNLOAD " + filename + "\n");

                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            if (brIn.readUTF().equals("OK")) {
                                ServerMulti.logger.info("Received from " + clientSock.getRemoteSocketAddress() + ": " + "OK" + "\n");
                                UsefulFunctions.receiveFile(filename, brIn);
                            }

                        } else {
                            // file is locked by someone else or is not locked at all
                            try {
                                pw.writeUTF("ERROR");
                                ServerMulti.logger.info("Reply to " + clientSock.getRemoteSocketAddress() + ": " + "ERROR" + "\n");

                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        }


                    }

                }

                // exit request
                if (message.equalsIgnoreCase("exit") && !isInterrupted()) {
                    ServerMulti.logger.info("Client quit: " + clientSock.getRemoteSocketAddress() + "\n");

                    break;

                }

                //a new client sent its unique name to this server, store the name of a new client in a hash map
                if (UsefulFunctions.get_request(message).equals("MYNAMEIS") && !isInterrupted()) {

                    ServerMulti.clientsIndexer.put((clientSock.getRemoteSocketAddress()), UsefulFunctions.get_path(message));

                }

                // the client might send this just to check of the server is up
                if (UsefulFunctions.get_request(message).equals("AREYOUALIVE") && !isInterrupted()) {

                    // do nothing, this is just a check for the client.

                }

//INSERT HERE
            }
        } catch (IOException e) {
            System.out.println("Error in communication.  Closing.");
            ServerMulti.logger.info("Error in communication.  Closing." + e.getMessage());

            e.printStackTrace();
        } finally {
            System.out.println("Finished and closed on " + clientSock.getRemoteSocketAddress().toString());


            ServerMulti.logger.info("Disconnected: " + clientSock.getRemoteSocketAddress() + "\n");


            try {
                clientSock.close();
            } catch (Exception ex) {
                ServerMulti.logger.info("Couldn't close socket properly" + ex.getMessage());

            }
        }

        return;
    }


}