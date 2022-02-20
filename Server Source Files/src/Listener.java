
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Listener extends Thread{


    private ServerSocket serverSocket;

    public Listener (ServerSocket socket){
        serverSocket = socket;
    }

    public List<HandleClientThread> threadList = new ArrayList<HandleClientThread>();



    @Override
    public void run() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        // start to listen on the server socket

        ServerMulti.logger.info("Listening on " + serverSocket.getInetAddress().toString() + ":" + serverSocket.getLocalPort()+"\n");


        System.out.println("Listening on " + serverSocket.getInetAddress().toString() + ":" + serverSocket.getLocalPort());
        while (!interrupted()) {
            try {
                // get a new connection
                Socket clientSocket = serverSocket.accept();
                // start a worker
                HandleClientThread clientThread = new HandleClientThread(clientSocket);
                threadList.add(clientThread);
                clientThread.start();
            } catch (Exception ex)
            {
                ServerMulti.logger.warning(ex.getMessage());
                // something is wrong, let's quit
            }
        }

        // we're done!
        for (int i = 0; i < threadList.size(); i++) {
            threadList.get(i).interrupt();
        }

        ServerMulti.logger.info("Stopped listening.\n");


        //System.out.println("Stopped listening.");
        try {
            serverSocket.close();
        } catch (Exception ex)
        {
            ServerMulti.logger.warning(ex.getMessage());
            //noting to do
        }
    }
}
