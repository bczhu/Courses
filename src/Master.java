/*##############################################################
## MODULE: Master.java
## VERSION: 1.0 
## SINCE: 2014-03-31
## AUTHOR: 
##     JIMMY LIN (xl5224) - JimmyLin@utexas.edu  
##     CALVIN SZETO - Szeto.calvin@gmail.com
## DESCRIPTION: 
##      
#################################################################
## Edited by MacVim
## Class Info auto-generated by Snippet 
################################################################*/

import java.util.Scanner;
import java.util.ArrayList;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Master extends Util {
    final static String RUN_SERVER_CMD = "java -cp ./bin/ Server";
    final static String RUN_CLIENT_CMD = "java -cp ./bin/ Client";

    static int leaderID;
    static int numNodes = -1, numClients = -1; // initialize to invalid value

    static InetAddress localhost;

    public static void checkAllClear (final ServerSocket listener) throws IOException, InterruptedException {
        // STEP ZERO: check the parameters
        assert (numClients > 0): "numClients not initialized.";
        assert (numNodes > 0): "numNodes not initialized";
        final Integer nClients1 = numClients;
        int clientIndex, nodeIndex;

        // STEP ONE: initialize an all false array saying no acks
        // received at first stage
        final ArrayList<Boolean> clientsCheckClear = new ArrayList<Boolean> ();
        for (clientIndex = 0; clientIndex < numClients; clientIndex ++) {
            clientsCheckClear.add(false);
        }
        // STEP TWO: start a new thread listenning to the ack
        Thread collectCheckClearAcks = new Thread (new Runnable() {
            public void run () {
                try {
                    while (true) {
                        Socket socket = listener.accept();
                        try { 
                            BufferedReader in = new BufferedReader(new
                                InputStreamReader(socket.getInputStream()));
                            String recMessage = in.readLine();
                            String [] recInfo = recMessage.split(",");
                            String title = recInfo[TITLE_IDX];
                            printReceivedMessage(recMessage, MASTER_LOG_HEADER);
                            if (title.equals(CHECK_CLEAR_ACK_TITLE)) {
                                String sender_type = recInfo[SENDER_TYPE_IDX];
                                if (sender_type.equals(CLIENT_TYPE)){
                                    int cindex = Integer.parseInt(recInfo[SENDER_INDEX_IDX]);
                                    clientsCheckClear.set(cindex, true);
                                }
                                // check if all clients have been clear about this
                                boolean isAllClear = true;
                                for (int cIdx = 0; cIdx < nClients1; cIdx ++) {
                                    if (!clientsCheckClear.get(cIdx)) {
                                        isAllClear = false; 
                                        break;
                                    }
                                }
                                // all clear, end up this thread
                                if (isAllClear) break;
                            } else {
                                continue;
                            }
                        } finally { socket.close(); }
                    }
                } catch (IOException e) {;} finally { ;}
            }
        });
        collectCheckClearAcks.start();

        // STEP THREE: send all CHECK_CLEAR message to all clients
        int port;
        for (clientIndex = 0; clientIndex < numClients; clientIndex ++) {
            port = CLIENT_PORT_BASE + clientIndex; 
            String checkClearMessage = String.format(MESSAGE, MASTER_TYPE,
                    0, CLIENT_TYPE, clientIndex, CHECK_CLEAR_TITLE, EMPTY_CONTENT);
            send (localhost, port, checkClearMessage, MASTER_LOG_HEADER);
        }

        // STEP FOUR: Busy waits until receipt of all clients' ack
        collectCheckClearAcks.join();
        return ;
    }

    public static void main(String [] args) throws IOException, InterruptedException {
        Scanner scan = new Scanner(System.in);
        int clientIndex, nodeIndex;
        Process [] serverProcesses = null;
        Process [] clientProcesses = null;
        // server socket of master
        localhost = InetAddress.getLocalHost();
        final ServerSocket listener = new ServerSocket(MASTER_PORT, 0,
                localhost);
        listener.setReuseAddress(true);

        while (scan.hasNextLine()) {
            int port;
            // parse the input instruction
            String input = scan.nextLine();
            String [] inputLine = input.split(" ");
            print(input, "[INPUT] ");
            
            // process creator
            Runtime runtime = Runtime.getRuntime();
            switch (inputLine[0]) {
                case "start":
                    numNodes = Integer.parseInt(inputLine[1]);
                    numClients = Integer.parseInt(inputLine[2]);
                    /*
                     * start up the right number of nodes and clients, and store the 
                     *  connections to them for sending further commands
                     */
                    // ============================================================
                    // DRIVEN BY JIMMY LIN STARTS
                    // STEP ONE: SET UP SERVERS AND CLIENTS
                    final ArrayList<Boolean> serversSetup = new ArrayList<Boolean> ();
                    for (nodeIndex = 0; nodeIndex < numNodes; nodeIndex ++) {
                        serversSetup.add(false);
                    }
                    final ArrayList<Boolean> clientsSetup = new ArrayList<Boolean> ();
                    for (clientIndex = 0; clientIndex < numClients; clientIndex ++) {
                        clientsSetup.add(false);
                    }

                    final Integer nServers = numNodes;
                    final Integer nClients = numClients;
                    Thread collectSetUpAcks = new Thread (new Runnable() {
                        public void run () {
                            try {
                            while (true) {
                            Socket socket = listener.accept();
                            try { 
                                BufferedReader in = new BufferedReader(new
                                        InputStreamReader(socket.getInputStream()));
                                String recMessage = in.readLine();
                                printReceivedMessage(recMessage, MASTER_LOG_HEADER);
                                String [] recInfo = recMessage.split(",");
                                if (recInfo[TITLE_IDX].equals(START_ACK_TITLE)) {
                                    int index = Integer.parseInt(recInfo[SENDER_INDEX_IDX]);
                                    if (recInfo[SENDER_TYPE_IDX].equals(SERVER_TYPE)) {
                                        serversSetup.set(index, true);
                                    } else if (recInfo[SENDER_TYPE_IDX].equals(CLIENT_TYPE)){
                                        clientsSetup.set(index, true);
                                    }
                                    // check if setup is complete
                                    boolean isSetUpComplete = true;
                                    for (int cIdx = 0; cIdx < nClients; cIdx ++) {
                                        if (!clientsSetup.get(cIdx)) { 
                                            isSetUpComplete = false; 
                                            break;
                                        }
                                    }
                                    for (int sIdx = 0; sIdx < nServers; sIdx ++) {
                                        if (!serversSetup.get(sIdx)) {
                                            isSetUpComplete = false;
                                            break;
                                        }
                                    }
                                    if (isSetUpComplete) {
                                        print("SETUP COMPLETES", MASTER_LOG_HEADER);
                                        break;
                                    }
                                } else {
                                    continue;
                                }
                            } finally { socket.close(); }
                            }
                            } catch (IOException e) {;} finally { ;}
                        }
                    });
                    collectSetUpAcks.start();

                    serverProcesses = new Process [numNodes];
                    clientProcesses = new Process [numClients];

                    for (clientIndex = 0; clientIndex < numClients; clientIndex ++) {
                        Integer clientID = new Integer(clientIndex);
                        String [] commandArray = new String [4];
                        commandArray[0] = RUN_CLIENT_CMD;
                        commandArray[1] = clientID.toString();
                        commandArray[2] = Integer.toString(numNodes);
                        commandArray[3] = Integer.toString(numClients);
                        String cmd = "";
                        for (int i = 0; i < commandArray.length; i++) {
                            cmd += " " + commandArray[i];
                        }
                        print(cmd, MASTER_LOG_HEADER);
                        Process pclient = runtime.exec(cmd);
                        clientProcesses[clientIndex] = pclient;
                    }

                    for (nodeIndex = 0; nodeIndex < numNodes; nodeIndex ++) {
                        Integer serverID = new Integer(nodeIndex);
                        String [] commandArray = new String [5];
                        commandArray[0] = RUN_SERVER_CMD;
                        commandArray[1] = serverID.toString();
                        commandArray[2] = Integer.toString(numNodes);
                        commandArray[3] = Integer.toString(numClients);
                        commandArray[4] = Integer.toString(0);   // not for recovery
                        String cmd = "";
                        for (int i = 0; i < commandArray.length; i++) {
                            cmd += " " + commandArray[i];
                        }
                        print (cmd, MASTER_LOG_HEADER);
                        Process pserver = runtime.exec(cmd); 
                        serverProcesses[nodeIndex] = pserver;
                    }
                    // Confirm all clients and servers have set up their listeners
                    collectSetUpAcks.join();

                    // STEP TWO: ELECT A NEW LEADER
                    // we use server 0 to carry leader
                    leaderID = 0;
                    final ArrayList<Boolean> serversLeaderACK = new ArrayList<Boolean> ();
                    for (nodeIndex = 0; nodeIndex < numNodes; nodeIndex ++) {
                        serversLeaderACK.add(false);
                    }
                    final ArrayList<Boolean> clientsLeaderACK = new ArrayList<Boolean> ();
                    for (clientIndex = 0; clientIndex < numClients; clientIndex ++) {
                        clientsLeaderACK.add(false);
                    }
                    // Create new thread to accept the leader response
                    Thread CollectLeaderResponse = new Thread (new Runnable () {
                        public void run () {
                            try {
                            while (true) {
                            Socket socket = listener.accept();
                            try { 
                                BufferedReader in = new BufferedReader(new
                                        InputStreamReader(socket.getInputStream()));
                                String recMessage = in.readLine();
                                printReceivedMessage(recMessage, MASTER_LOG_HEADER);
                                String [] recInfo = recMessage.split(",");
                                String title = recInfo[TITLE_IDX];
                                String sender_type = recInfo[SENDER_TYPE_IDX];
                                int sender_idx = Integer.parseInt(recInfo[SENDER_INDEX_IDX]);
                                if (title.equals(LEADER_ACK_TITLE)) {
                                    if (sender_type.equals(SERVER_TYPE)) {
                                        serversLeaderACK.set(sender_idx, true);
                                    } else if (sender_type.equals(CLIENT_TYPE)) {
                                        clientsLeaderACK.set(sender_idx, true);
                                    }
                                }
                                // check if setup is complete
                                boolean isACKComplete = true;
                                for (int cIdx = 0; cIdx < nClients; cIdx ++) {
                                    if (!clientsLeaderACK.get(cIdx)) { 
                                        isACKComplete = false; 
                                        break;
                                    }
                                }
                                for (int sIdx = 0; sIdx < nServers; sIdx ++) {
                                    if (!serversLeaderACK.get(sIdx)) {
                                        isACKComplete = false;
                                        break;
                                    }
                                }
                                if (isACKComplete) {
                                    print("LEADER ACK COLLECTION COMPLETE with LEADER " + leaderID,
                                            MASTER_LOG_HEADER); 
                                    break;
                                } else {
                                    continue;
                                }
                            } finally { socket.close(); }
                            }
                            } catch (IOException e) {;} finally { ;}
                        }
                    });
                    CollectLeaderResponse.start();
                    // Send message to tell the result of election
                    String eLeaderMsg;
                    String leaderStr = Integer.toString(leaderID);
                    for (nodeIndex = 0; nodeIndex < numNodes; nodeIndex ++) {
                        eLeaderMsg = String.format(MESSAGE, MASTER_TYPE, 0,
                                SERVER_TYPE, nodeIndex, LEADER_REQUEST_TITLE,
                                leaderStr);
                        port = SERVER_PORT_BASE + nodeIndex;
                        send (localhost, port, eLeaderMsg, MASTER_LOG_HEADER);
                    }
                    for (clientIndex = 0; clientIndex < numClients; clientIndex++) {
                        eLeaderMsg = String.format(MESSAGE, MASTER_TYPE, 0,
                                CLIENT_TYPE, clientIndex, LEADER_REQUEST_TITLE,
                                leaderStr);
                        port = CLIENT_PORT_BASE + clientIndex;
                        send (localhost, port, eLeaderMsg, MASTER_LOG_HEADER);
                    }
                    // Wait for the leader response
                    CollectLeaderResponse.join();

                    // ============================================================
                    break;
                case "sendMessage":
                    clientIndex = Integer.parseInt(inputLine[1]);
                    assert (numClients > 0): "numClients not initialized.";
                    assert (numNodes > 0): "numNodes not initialized";
                    assert (numClients > clientIndex): "clientIndex out of bound.";
                    String message = "";
                    for (int i = 2; i < inputLine.length; i++) {
                        message += inputLine[i];
                        if (i != inputLine.length - 1) {
                            message += " ";
                        }
                    }
                    /*
                     * Instruct the client specified by clientIndex to send the message
                     * to the proper paxos node
                     */
                    port = CLIENT_PORT_BASE + clientIndex;
                    String pmessage = String.format(MESSAGE, MASTER_TYPE, 0, CLIENT_TYPE, clientIndex, 
                            SEND_MESSAGE_TITLE, message);
                    send (localhost, port, pmessage, MASTER_LOG_HEADER);
                    break;
                case "printChatLog":
                    clientIndex = Integer.parseInt(inputLine[1]);
                    assert (numClients > 0): "numClients not initialized.";
                    assert (numNodes > 0): "numNodes not initialized";
                    /*
                     * Print out the client specified by clientIndex's chat history
                     * in the format described on the handout.	     
                     *
                     * NOTE that the chat log should be printed by master
                     */
                    Thread collectChatLog = new Thread (new Runnable() {
                        public void run () {
                            try { while (true) {
                                Socket socket = listener.accept();
                                try { 
                                    BufferedReader in = new BufferedReader(new
                                        InputStreamReader(socket.getInputStream()));
                                    String recMessage = in.readLine();
                                    printReceivedMessage(recMessage, MASTER_LOG_HEADER);
                                    String [] recInfo = recMessage.split(",");
                                    if (recInfo[TITLE_IDX].equals(HERE_IS_CHAT_LOG_TITLE)) {
                                        String chatLogs = recInfo[CONTENT_IDX];
                                        // check if setup is complete
                                        boolean isChatLogReceived = true;
                                        // decode the content
                                        String [] chatLogsPart = chatLogs.split(CHAT_PIECE_SEP);
                                        for (int clIdx = 0; clIdx < chatLogsPart.length; clIdx ++) {
                                            // print out the received chat log
                                            System.out.println(chatLogsPart[clIdx]);
                                        }
                                        if (isChatLogReceived) {
                                            break;
                                        }
                                    } else {
                                        continue;
                                    }
                                } finally { socket.close(); }
                            }
                            } catch (IOException e) {;} finally { ;}
                        }
                    });
                    collectChatLog.start();
                    // STEP TWO: send message to client asking for chat log
                    port = CLIENT_PORT_BASE + clientIndex; 
                    String tmp_message = String.format(MESSAGE, MASTER_TYPE,
                            0, CLIENT_TYPE, clientIndex, PRINT_CHAT_LOG_TITLE, EMPTY_CONTENT);
                    send (localhost, port, tmp_message, MASTER_LOG_HEADER);
                    // STEP THREE: wait until the chat log is received
                    collectChatLog.join();
                    break;
                case "allClear":
                    /*
                     * Ensure that this blocks until all messages that are going to 
                     * come to consensus in PAXOS do, and that all clients have heard
                     * of them 
                     *
                     * NOTE: Our solution is to check whether all clients
                     * receive all messages they sent.
                     */
                    checkAllClear(listener);
                    break;
                case "crashServer":
                    nodeIndex = Integer.parseInt(inputLine[1]);
                    /*
                     * Immediately crash the server specified by nodeIndex
                     */
                    // ======================================================
                    // We directly kill that process
                    // serverProcesses[nodeIndex].destroy();
                    // serverProcesses[nodeIndex] = null;
                    // ======================================================
                    break;
                case "restartServer":
                    nodeIndex = Integer.parseInt(inputLine[1]);
                    /*
                     * Restart the server specified by nodeIndex
                     */
                    assert(serverProcesses[nodeIndex] == null): "Do not restart an uncrashed server.";
                    Integer serverID = new Integer(nodeIndex);
                    String [] commandArray = new String [5];
                    commandArray[0] = RUN_SERVER_CMD;
                    commandArray[1] = serverID.toString();
                    commandArray[2] = Integer.toString(numNodes);
                    commandArray[3] = Integer.toString(numClients);
                    commandArray[4] = Integer.toString(1);   // for recovery
                    String cmd = "";
                    for (int i = 0; i < commandArray.length; i++) {
                        cmd += " " + commandArray[i];
                    }
                    print (cmd, MASTER_LOG_HEADER);
                    Process pserver = runtime.exec(cmd); 
                    serverProcesses[nodeIndex] = pserver;
                    break;
                case "skipSlots":
                    int amountToSkip = Integer.parseInt(inputLine[1]);
                    /*
                     * Instruct the leader to skip slots in the chat message sequence  
                     */ 
                    // STEP ZERO: check if all clients are clear
                    checkAllClear(listener);
                    // STEP ONE: create a thread and start to collect ack
                    final ArrayList<Boolean> serversSkipSlotACK = new ArrayList<Boolean> ();
                    for (nodeIndex = 0; nodeIndex < numNodes; nodeIndex ++) {
                        serversSkipSlotACK.add(false);
                    }
                    final Integer nServers2 = numNodes;
                    Thread collectSkipSlotsAcks = new Thread (new Runnable () {
                        public void run () {
                            try {
                            while (true) {
                            Socket socket = listener.accept();
                            try { 
                                BufferedReader in = new BufferedReader(new
                                        InputStreamReader(socket.getInputStream()));
                                String recMessage = in.readLine();
                                printReceivedMessage(recMessage, MASTER_LOG_HEADER);
                                String [] recInfo = recMessage.split(",");
                                String title = recInfo[TITLE_IDX];
                                String sender_type = recInfo[SENDER_TYPE_IDX];
                                int sender_idx = Integer.parseInt(recInfo[SENDER_INDEX_IDX]);
                                if (title.equals(SKIP_SLOT_ACK_TITLE)) {
                                    if (sender_type.equals(SERVER_TYPE)) {
                                        serversSkipSlotACK.set(sender_idx, true);
                                    } 
                                }
                                // check if all acks come
                                boolean isACKComplete = true;
                                for (int sIdx = 0; sIdx < nServers2; sIdx ++) {
                                    if (!serversSkipSlotACK.get(sIdx)) {
                                        isACKComplete = false;
                                        break;
                                    }
                                }
                                if (isACKComplete) {
                                    print("Skip Slots Completes.", MASTER_LOG_HEADER); 
                                    break;
                                } else {
                                    continue;
                                }
                            } finally { socket.close(); }
                            }
                            } catch (IOException e) {;} finally { ;}
                        }
                    });
                    collectSkipSlotsAcks.start();
                    // STEP TWO: send skipSlots instruction to all replicas
                    for (nodeIndex = 0; nodeIndex < numNodes; nodeIndex ++) {
                        port = SERVER_PORT_BASE + nodeIndex;
                        String SkipSlotMessage = String.format(MESSAGE,
                                MASTER_TYPE, 0, SERVER_TYPE, nodeIndex,
                                SKIP_SLOT_TITLE, Integer.toString(amountToSkip));
                        send(localhost, port, SkipSlotMessage, MASTER_LOG_HEADER);
                    }
                    // STEP THREE: wait the collection thread to join
                    collectSkipSlotsAcks.join();
                    break;
                case "timeBombLeader": // death within protocol
                    int numMessages = Integer.parseInt(inputLine[1]);
                    /*
                     * Instruct the leader to crash after sending the number of paxos
                     * related messages specified by numMessages
                     */ 
                    // Send timeBomb instruction to server with leaderID
                    port = SERVER_PORT_BASE + leaderID;
                    String timeBombMessage = String.format(MESSAGE,
                        MASTER_TYPE, 0, SERVER_TYPE, leaderID,
                        TIME_BOMB_TITLE, Integer.toString(numMessages));
                    send(localhost, port, timeBombMessage, MASTER_LOG_HEADER);
                    break;
            }
        }
        /* Ask all clients and server to terminate */
        checkAllClear(listener);
        if (clientProcesses != null) {
            for (clientIndex = 0; clientIndex < clientProcesses.length; clientIndex ++) {
                if (clientProcesses[clientIndex] != null) {
                    int port = CLIENT_PORT_BASE + clientIndex;
                    String exitMessage = String.format(MESSAGE, MASTER_TYPE, 0,
                            CLIENT_TYPE, clientIndex, EXIT_TITLE, EMPTY_CONTENT);
                    send(localhost, port, exitMessage, MASTER_LOG_HEADER);
                }
            }
        }
        if (serverProcesses != null) {
            for (nodeIndex = 0; nodeIndex < serverProcesses.length; nodeIndex ++) {
                if (serverProcesses[nodeIndex] != null) {
                    InetAddress host = InetAddress.getLocalHost();
                    int port = SERVER_PORT_BASE + nodeIndex;
                    String exitMessage = String.format(MESSAGE, MASTER_TYPE, 0,
                            SERVER_TYPE, nodeIndex, EXIT_TITLE, EMPTY_CONTENT);
                    send(localhost, port, exitMessage, MASTER_LOG_HEADER);
                }
            }
        }
        listener.close();
    }
}
