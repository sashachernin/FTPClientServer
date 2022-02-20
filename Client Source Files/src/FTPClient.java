import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import javax.swing.*;
import java.awt.event.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class FTPClient {
    private JButton downloadButton;
    private JTextField fileNameText;
    private JPanel mainPanel;
    private JComboBox<String> serversComboBox;
    private JButton lockButton;
    private JButton unlockButton;
    private JButton getVersionButton;
    private JButton browseButton;
    private JButton uploadButton;
    private JTextArea statusTextArea;
    private JButton getFilesListButton;
    private JLabel pathLabel;
    private JButton reconnect;
    static JDialog jdialog;

    private static String path;

    public static ConcurrentHashMap<String, ServerObj> servers = new ConcurrentHashMap<>();

    //num of connected servers
    private static int countOfActiveServers = 0;

    //set name for this client by generating a random alphabetic string, with the length of 10 letters
    public static String clientName = GeneratingRandomAlphabetic();

    public FTPClient() {


        //selected server action
        serversComboBox.addItemListener(event -> {
            // The item affected by the event.
            String item = (String) event.getItem();

            if (event.getStateChange() == ItemEvent.SELECTED) {
                if (!item.equals("all")) {
                    uploadButton.setEnabled(false);
                    lockButton.setEnabled(false);
                    unlockButton.setEnabled(false);
                    downloadButton.setEnabled(true);
                    getFilesListButton.setEnabled(true);
                } else {
                    uploadButton.setEnabled(true);
                    lockButton.setEnabled(true);
                    unlockButton.setEnabled(true);
                    downloadButton.setEnabled(false);
                    getFilesListButton.setEnabled(false);


                }
            }

            /*if (event.getStateChange() == ItemEvent.DESELECTED) {
                textArea.append(item + " deselected\n");
            }*/
        });

        //browse button
        browseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Choose file to upload");
                fileChooser.setSelectedFile(new File(fileNameText.getText()));

                if (fileChooser.showOpenDialog(browseButton) == JFileChooser.APPROVE_OPTION) {

                    path = fileChooser.getSelectedFile().toString();
                    pathLabel.setText(getFilenameFromPath(path));

                }


            }
        });

        //upload - communication with server
        uploadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                statusTextArea.setText("");
                //get file name
                String fileName = getFilenameFromPath(path);
                ServerObj selectedServer = null;


                //upload to single server
                //find first available server
                for (String key : servers.keySet()) {
                    if (servers.get(key).getSocket() != null) {
                        //send a test message to a server
                        try {
                            servers.get(key).getPwOut().writeUTF("AREYOUALIVE");
                        } catch (IOException ex) {
                            System.out.println("couldn't send message to server, try other");
                            servers.remove(key);
                            continue;
                        }

                        selectedServer = servers.get(key);
                        break;


                    }
                }

                try {
                    System.out.println("sending UPLOAD message to " + selectedServer);
                    selectedServer.getPwOut().writeUTF("UPLOAD " + fileName);

                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                statusTextArea.setText("uploading...");
                ServerObj finalSelectedServer = selectedServer;
                new Thread(() -> {
                    // Insert some method call here.
                    try {
                        sendFile(path, finalSelectedServer.getPwOut());
                        if (finalSelectedServer.getBrIn().readUTF().equals("OK")) {
                            statusTextArea.setText("Finished upload to all servers: " + pathLabel.getText());
                            pathLabel.setText("");
                        } else {
                            statusTextArea.setText("Failed to upload to all servers: " + pathLabel.getText());
                            pathLabel.setText("");
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        statusTextArea.setText("server dropped connection couldn't upload");
                    }


                }).start();

            }
        });


        //download case - communication with server
        downloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (fileNameText.getText().equals("")) {
                    statusTextArea.setText("Please enter file's name");
                } else {
                    JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setDialogTitle("Choose download destination");
                    fileChooser.setSelectedFile(new File(fileNameText.getText()));

                    if (fileChooser.showSaveDialog(downloadButton) == JFileChooser.APPROVE_OPTION) {

                        String fileName = fileNameText.getText();
                        String command = "DOWNLOAD " + fileName;

                        new Thread(() -> {
                            // Insert some method call here.
                            try {
                                servers.get(String.valueOf(serversComboBox.getSelectedItem())).getPwOut().writeUTF(command);
                                if (servers.get(String.valueOf(serversComboBox.getSelectedItem())).getBrIn().readUTF().equals("OK")) {
                                    statusTextArea.setText("starting download");
                                    receiveFile("temp", servers.get(String.valueOf(serversComboBox.getSelectedItem())).getBrIn());
                                    Path temp = Files.move(Paths.get("./temp"), Paths.get(fileChooser.getSelectedFile().toString()), REPLACE_EXISTING);
                                    statusTextArea.setText("Download completed");
                                } else
                                    statusTextArea.setText("no such file on server");
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }


                            fileNameText.setText("");

                        }).start();

                    }

                }
            }
        });

        //get version - communication with server
        getVersionButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                statusTextArea.setText("");


                String fileName = fileNameText.getText();
                if (!Objects.equals(fileName, "")) {

                    String command = "GETVERSION " + fileName;

                    if (serversComboBox.getSelectedItem().equals("all")) {

                        AtomicReference<String> responseFromServers = new AtomicReference<String>("");
                        for (String key : servers.keySet()) {

                            new Thread(() -> {
                                // Insert some method call here.
                                try {
                                    servers.get(key).getPwOut().writeUTF(command);
                                    String response = servers.get(key).getBrIn().readUTF();
                                    if (!response.equals("ERROR")) {
                                        appendStatus(key + ":" + "\n" + versionFormat(response) + "\n", FTPClient.this);

                                    } else {
                                        appendStatus(key + ":" + "\n" + "No such file on the server: " + fileNameText.getText() + "\n", FTPClient.this);
                                    }

                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                                fileNameText.setText("");
                            }).start();


                        }

                    } else {

                        new Thread(() -> {
                            // Insert some method call here.
                            try {
                                servers.get(String.valueOf(serversComboBox.getSelectedItem())).getPwOut().writeUTF(command);
                                String response = servers.get(String.valueOf(serversComboBox.getSelectedItem())).getBrIn().readUTF();
                                if (!response.equals("ERROR"))
                                    appendStatus(serversComboBox.getSelectedItem() + ":" + "\n" + versionFormat(response) + "\n", FTPClient.this);
                                else
                                    statusTextArea.setText(serversComboBox.getSelectedItem() + ":\n" + "No such file on the server: " + fileName);


                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                            fileNameText.setText("");
                        }).start();
                    }
                } else {
                    statusTextArea.setText("Please enter file's name");
                }
            }
        });

        //get list of file - communication with server
        getFilesListButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (serversComboBox.getSelectedItem().equals("all")) {
                    statusTextArea.setText("use must select a specific server for this option");
                } else {
                    String command = "GETLIST";
                    new Thread(() -> {
                        // Insert some method call here.
                        try {
                            servers.get(String.valueOf(serversComboBox.getSelectedItem())).getPwOut().writeUTF(command);
                            String files = servers.get(String.valueOf(serversComboBox.getSelectedItem())).getBrIn().readUTF();
                            if (files.equals("FILELIST"))
                                statusTextArea.setText("There's no files on the chosen server");
                            else
                                statusTextArea.setText(formatFileList(files));
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }

                    }).start();
                }

            }
        });


        //lock request
        lockButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (fileNameText.getText().equals(""))
                    statusTextArea.setText("Please enter file's name");
                else {
                    statusTextArea.setText("");

                    ServerObj selectedServer = null;


                    //upload to single server
                    //find first available server
                    for (String key : servers.keySet()) {
                        if (servers.get(key).getSocket() != null) {
                            //send a test message to a server
                            try {
                                servers.get(key).getPwOut().writeUTF("AREYOUALIVE");
                            } catch (IOException ex) {
                                System.out.println("couldn't send message to server, try other");
                                servers.remove(key);
                                continue;
                            }
                            //available server found. add later: try sending it a message to check of its online
                            selectedServer = servers.get(key);
                            break;


                        }
                    }

                    String command = "LOCK " + fileNameText.getText() + " " + clientName;
                    System.out.println("the message that is sent to the server: " + command);


                    ServerObj finalSelectedServer = selectedServer;
                    new Thread(() -> {
                        // Insert some method call here.
                        try {
                            finalSelectedServer.getPwOut().writeUTF(command);

                            if (finalSelectedServer.getBrIn().readUTF().equals("OK"))
                                statusTextArea.setText("File locked on all servers: " + fileNameText.getText());
                            else
                                statusTextArea.setText("Can't lock file on all servers: " + fileNameText.getText());

                            //to finish
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                        fileNameText.setText("");
                    }).start();
                }

            }
        });

        //unlock request
        unlockButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (fileNameText.getText().equals(""))
                    statusTextArea.setText("Please enter file's name");
                else {

                    statusTextArea.setText("");

                    ServerObj selectedServer = null;


                    //upload to single server
                    //find first available server
                    for (String key : servers.keySet()) {
                        if (servers.get(key).getSocket() != null) {
                            //send a test message to a server
                            try {
                                servers.get(key).getPwOut().writeUTF("AREYOUALIVE");
                            } catch (IOException ex) {
                                System.out.println("couldn't send message to server, try other");
                                servers.remove(key);
                                continue;
                            }
                            //available server found. add later: try sending it a message to check of its online
                            selectedServer = servers.get(key);
                            break;


                        }
                    }

                    String command = "UNLOCK " + fileNameText.getText() + " " + clientName;

                    if (serversComboBox.getSelectedItem().equals("all")) {
                        AtomicReference<String> responseFromServers = new AtomicReference<String>("");


                        ServerObj finalSelectedServer = selectedServer;
                        new Thread(() -> {
                            // Insert some method call here.
                            try {
                                finalSelectedServer.getPwOut().writeUTF(command);

                                if (finalSelectedServer.getBrIn().readUTF().equals("OK"))
                                    statusTextArea.setText("File unlocked: " + fileNameText.getText());
                                else
                                    statusTextArea.setText("Can't Unlock file: " + fileNameText.getText());

                                //to finish
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                            fileNameText.setText("");
                        }).start();


                        statusTextArea.setText(responseFromServers.get());
                    }
                }
            }
        });

        //close event of this app
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    for (String key : servers.keySet()) {
                        if (servers.get(key).getSocket() != null) {
                            servers.get(key).getPwOut().writeUTF("exit");

                            servers.get(key).getPwOut().close();
                            servers.get(key).getBrIn().close();
                            servers.get(key).getSocket().close();
                        }

                    }

                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

    }

    /**
     * @param args
     */
    public static void main(String[] args) {


        JFrame window = new JFrame();
        FTPClient client = new FTPClient();
        window.setContentPane(client.mainPanel);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.pack();
        window.setVisible(true);

        //setting style of window
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        } catch (UnsupportedLookAndFeelException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }

        SwingUtilities.updateComponentTreeUI(window);

        client.serversComboBox.addItem("all");


        //get ip address and port from config file
        String serverName = "";
        String ipAddress = null;
        int portNum = 0;

        //initial try to connect to servers from config.txt
        try (BufferedReader br = new BufferedReader(new FileReader("config.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                ipAddress = getIPnPort(line)[0];
                portNum = Integer.parseInt(getIPnPort(line)[1]);
                serverName = get_request(line);

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
                DataInputStream brIn = null;
                DataOutputStream pwOut = null;
                try {
                    sock = new Socket(add, portNum);

                    // connect the readers and writers
                    brIn = new DataInputStream(sock.getInputStream());
                    pwOut = new DataOutputStream(sock.getOutputStream());
                } catch (IOException iox) {
                    System.out.println("Error connecting to the server: " + iox.getMessage());
                    System.out.println(sock);
                }

                ServerObj srv = new ServerObj(sock, brIn, pwOut, add, portNum, serverName);
                servers.put(serverName, srv);
                if (srv.getSocket() != null) { //if the connection is successful

                    //add server name to combobox
                    client.serversComboBox.addItem(serverName);
                    countOfActiveServers++;

                    //send your name to the client
                    srv.getPwOut().writeUTF("MYNAMEIS " + clientName);
                }


            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        //thread that tries to reconnect to failed connection
        new Thread(() -> {
            while (true)
                reconnectNullConnection(client, servers);
        }).start();


    }

    /*Receives full path, returns only the files name*/
    public static String getFilenameFromPath(String cmd) {

        Path path = Paths.get(cmd);

        return path.getFileName().toString();
    }

    /*
     *Receives a file name and DataOutputStream
     *Finds given file name in the storage path, and sends its content with the given DataOutputStream
     */
    private static void sendFile(String path, DataOutputStream dos) {
        int bytes = 0;
        File file = new File(path);
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);
            // send file size
            dos.writeLong(file.length());
            // break file into chunks
            byte[] buffer = new byte[4 * 1024];
            while ((bytes = fileInputStream.read(buffer)) != -1) {
                dos.write(buffer, 0, bytes);
                dos.flush();
            }
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();

        }


    }

    /*
     *Receives a file name and DataInputStream
     *Creates a file in the storage path with the given name, and receives its content with the given DataInputStream
     */
    public static void receiveFile(String fileName, DataInputStream dis) {
        int bytes = 0;
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(fileName);
            long size = dis.readLong();     // read file size
            byte[] buffer = new byte[4 * 1024];
            while (size > 0 && (bytes = dis.read(buffer, 0, (int) Math.min(buffer.length, size))) != -1) {
                fileOutputStream.write(buffer, 0, bytes);
                size -= bytes;      // read upto file size
            }
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    /*Receives FILELIST string from the server, delimits it and formats it in more aesthetic way*/
    public static String formatFileList(String list) {
        String output = "";
        String temp = get_path(list);
        String[] names = temp.split(":");

        for (String name : names)
            output += name + "\n";


        return output;

    }

    /*
     *Receives a string that contains a space, returns the word after space
     *example: receives "Hello World", returns "World"
     */
    public static String get_path(String cmd) {

        String[] req = cmd.split(" ", 2);
        return req[1];
    }

    /*Receives version string from the server, delimits it and returns datetime and hash digest only*/
    public static String versionFormat(String str) {

        String[] req = str.split(" ", 4);
        return req[1] + " " + req[2] + "\n";
    }

    /*
     * Receives line from the config.txt file, returns the IP and Port of the server
     * ex. receives "serverOne 10.0.201.5:4000", returns array ["10.0.201.5", "4000"]
     */
    public static String[] getIPnPort(String line) {
        String temp = get_path(line);
        String[] iPort = temp.split(":");

        return iPort;

    }

    /*
     *Receives a string that contains a space, returns the word before space
     *example: receives "Hello World", returns "Hello"
     */
    public static String get_request(String cmd) {

        String[] req = cmd.split(" ", 2);
        return req[0];
    }


    public static synchronized void appendStatus(final String a, FTPClient client) {
        // safe
        client.statusTextArea.append(a);
    }

    /*function that constantly running and trying to connect to servers in the config list*/
    public static void reconnectNullConnection(FTPClient client, ConcurrentHashMap<String, ServerObj> servers) {

        for (String key : servers.keySet()) {
            if (servers.get(key).getSocket() == null) {

                //create socket
                Socket sock = null;
                DataInputStream brIn = null;
                DataOutputStream pwOut = null;
                try {
                    sock = new Socket(servers.get(key).getAdd(), servers.get(key).getPort());

                    // connect the readers and writers
                    brIn = new DataInputStream(sock.getInputStream());
                    pwOut = new DataOutputStream(sock.getOutputStream());
                    servers.get(key).setSocket(sock, brIn, pwOut);
                    //add server name to combobox
                    client.serversComboBox.addItem(servers.get(key).getName());
                    countOfActiveServers++;
                    if (countOfActiveServers == 2)
                        client.serversComboBox.addItem("all");

                    //send your name to the client
                    servers.get(key).getPwOut().writeUTF("MYNAMEIS " + clientName);

                } catch (IOException iox) {


                }

            }

        }


    }

    /*
     * generates a random alphabetic string
     * used as client's ID that is sent to the server when a connection is established
     */
    public static String GeneratingRandomAlphabetic() {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 10;
        Random random = new Random();

        String generatedString = random.ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();

        return generatedString;
    }


}
