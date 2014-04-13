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
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Semaphore;

public class Master extends Util {
    final static String RUN_SERVER_CMD = "java -cp ./bin/ Server";
    final static String RUN_CLIENT_CMD = "java -cp ./bin/ Client";

    static int leaderID;
    static int numNodes = -1, numClients = -1; // initialize to invalid value

    static InetAddress localhost;
    static String logfilename;
    static PrintStream original;
    static PrintStream log;
    static PrintWriter writer;

    static ServerSocket masterListener;
    static ArrayList<Boolean> clientsCheckClear;
    static ArrayList<Boolean> serversCheckClear;
    static ArrayList<Boolean> serversSkipSlotACK;
    static ArrayList<Boolean> serversSetup;
    static ArrayList<Boolean> clientsSetup;
    static ArrayList<Boolean> serversLeaderACK;
    static ArrayList<Boolean> clientsLeaderACK;

    static Process [] serverProcesses;
    static Process [] clientProcesses;

    static Semaphore setupSema;
    static Semaphore ackLeaderSema;
    static Semaphore checkClearSema;
    static Semaphore skipSlotsSema;

    public static void checkAllClear () throws IOException, InterruptedException {
        // STEP ZERO: check the parameters
        assert (numClients > 0): "numClients not initialized.";
        assert (numNodes > 0): "numNodes not initialized";
        // final Integer nClients1 = numClients;
        int clientIndex, serverIndex;

        // STEP ONE: initialize an all false array saying no acks
        // received at first stage
        clientsCheckClear = new ArrayList<Boolean> ();
        serversCheckClear = new ArrayList<Boolean> ();
        for (clientIndex = 0; clientIndex < numClients; clientIndex ++) {
            clientsCheckClear.add(false);
        }
        for (serverIndex = 0; serverIndex < numNodes; serverIndex ++) {
            serversCheckClear.add(false);
        }
        // STEP TWO: initialize a new semaphore with no permit
        checkClearSema = new Semaphore (0, true);
        // STEP THREE: send all CHECK_CLEAR message to all clients and servers
        int port;
        for (clientIndex = 0; clientIndex < numClients; clientIndex ++) {
            port = CLIENT_PORT_BASE + clientIndex; 
            String checkClearMessage = String.format(MESSAGE, MASTER_TYPE,
                    0, CLIENT_TYPE, clientIndex, CHECK_CLEAR_TITLE, EMPTY_CONTENT);
            send (localhost, port, checkClearMessage, MASTER_LOG_HEADER);
        }
        for (serverIndex = 0; serverIndex < numNodes; serverIndex ++) {
            port = SERVER_PORT_BASE + serverIndex; 
            String checkClearMessage = String.format(MESSAGE, MASTER_TYPE,
                    0, SERVER_TYPE, serverIndex, CHECK_CLEAR_TITLE, EMPTY_CONTENT);
            boolean success = send (localhost, port, checkClearMessage, MASTER_LOG_HEADER);
            if(!success) {
                serversCheckClear.set(serverIndex, true);
            }
        }
        // STEP FOUR: Busy waits until receipt of all clients' ack
        checkClearSema.acquire();
        return ;
    }

    // create a thread dealing with the normal messages
    static class MasterRoutineExecutor extends Thread implements Runnable {
        public void run () {
            Socket socket = null;
            while (true) {
                try {
                    socket = masterListener.accept();
                    BufferedReader in = new BufferedReader(new
                            InputStreamReader(socket.getInputStream()));
                    String recMessage = in.readLine();
                    printReceivedMessage(recMessage, MASTER_LOG_HEADER);
                    String [] recInfo = recMessage.split(MESSAGE_SEP);

                    String sender_type = recInfo[SENDER_TYPE_IDX];
                    int sender_idx = Integer.parseInt(recInfo[SENDER_INDEX_IDX]);
                    String receiver_type = recInfo[RECEIVER_TYPE_IDX];
                    int receiver_idx = Integer.parseInt(recInfo[RECEIVER_INDEX_IDX]);
                    String title = recInfo[TITLE_IDX];
                    String content = recInfo[CONTENT_IDX];

                    /* SYSTEM CONFIGURATION: collects start-up acknowledgement */
                    if (title.equals(START_ACK_TITLE)) {
                        if (sender_type.equals(SERVER_TYPE)) {
                            serversSetup.set(sender_idx, true);
                        } else if (sender_type.equals(CLIENT_TYPE)){
                            clientsSetup.set(sender_idx, true);
                        }
                        // check if setup is complete
                        boolean isSetUpComplete = true;
                        for (int cIdx = 0; cIdx < numClients; cIdx ++) {
                            if (!clientsSetup.get(cIdx)) { 
                                isSetUpComplete = false; 
                                break;
                            }
                        }
                        for (int sIdx = 0; sIdx < numNodes; sIdx ++) {
                            if (!serversSetup.get(sIdx)) {
                                isSetUpComplete = false;
                                break;
                            }
                        }
                        if (isSetUpComplete) {
                            print("SETUP COMPLETES", MASTER_LOG_HEADER);
                            setupSema.release();
                        }
                    }
                    /* Leader Acknowledgement */
                    else if (title.equals(LEADER_ACK_TITLE)) {
                        if (sender_type.equals(SERVER_TYPE)) {
                            serversLeaderACK.set(sender_idx, true);
                        } else if (sender_type.equals(CLIENT_TYPE)) {
                            clientsLeaderACK.set(sender_idx, true);
                        }
                        // check if setup is complete
                        boolean isACKComplete = true;
                        for (int cIdx = 0; cIdx < numClients; cIdx ++) {
                            if (!clientsLeaderACK.get(cIdx)) { 
                                isACKComplete = false; 
                                break;
                            }
                        }
                        for (int sIdx = 0; sIdx < numNodes; sIdx ++) {
                            if (!serversLeaderACK.get(sIdx)) {
                                isACKComplete = false;
                                break;
                            }
                        }
                        if (isACKComplete) {
                            print("LEADER ACK COLLECTION COMPLETE with LEADER " + leaderID,
                                    MASTER_LOG_HEADER); 
                            ackLeaderSema.release();
                        }
                    }
                    /* LEADER ELECTION PROTOCOL: update the master's knowledge of who is new leader */
                    else if (title.equals(LEADER_REQUEST_TITLE)) {
                        // STEP ONE: remove the process that carry previous leader
                        serverProcesses[leaderID] = null;
                        String recheckClearAcks = String.format(MESSAGE,
                                MASTER_TYPE, 0, MASTER_TYPE, 0,
                                CHECK_CLEAR_TITLE, EMPTY_CONTENT);
                        send (localhost, MASTER_PORT, recheckClearAcks, MASTER_LOG_HEADER);
                        // STEP TWO: change the leaderID
                        leaderID = sender_idx;
                        // STEP THREE: make statement
                        print ("I know that Server " + sender_idx + 
                                " is new leader." , MASTER_LOG_HEADER);
                    }
                    /* printChatLog INSTRUCTION: print the received chat log */
                    else if (title.equals(HERE_IS_CHAT_LOG_TITLE)) {
                        // STEP ONE: decode the content
                        String [] chatLogs = content.split(CHAT_PIECE_SEP);
                        // STEP TWO: print out the received chat log
                        for (int clIdx = 0; clIdx < chatLogs.length; clIdx ++) {
                            System.out.println(chatLogs[clIdx]);
                            writer.println(chatLogs[clIdx]);
                        }
                    }
                    /* allClear INSTRUCTION: collects acknowledgement */
                    else if (title.equals(CHECK_CLEAR_ACK_TITLE)) {
                        // STEP ONE: update the cached check clear arraylist
                        if (sender_type.equals(CLIENT_TYPE)) {
                            clientsCheckClear.set(sender_idx, true);
                        } else if (sender_type.equals(SERVER_TYPE)) {
                            serversCheckClear.set(sender_idx, true);
                        }
                        // STEP TWO: check if all clients have been clear about this
                        boolean isAllClear = true;
                        for (int cIdx = 0; cIdx < numClients; cIdx ++) {
                            if (clientProcesses[cIdx] == null) continue;
                            if (!clientsCheckClear.get(cIdx)) {
                                isAllClear = false; 
                                break;
                            }
                        }
                        for (int sIdx = 0; sIdx < numNodes; sIdx ++) {
                            // NOTE: ignore the dead server
                            if (serverProcesses[sIdx] == null) continue;
                            if (!serversCheckClear.get(sIdx)) {
                                isAllClear = false; 
                                break;
                            }
                        }
                        // STEP THREE: signal and unblock the main thread 
                        if (isAllClear) {
                            checkClearSema.release();
                        }
                    }
                    /* skipSlot INSTRUCTION: collects acknowledgement */
                    else if (title.equals(SKIP_SLOT_ACK_TITLE)) {
                        // STEP ONE: update the cached skip slot acks arraylist
                        if (sender_type.equals(SERVER_TYPE)) {
                            serversSkipSlotACK.set(sender_idx, true);
                        } 
                        // STEP TWO: check if all acks come
                        boolean isACKComplete = true;
                        for (int sIdx = 0; sIdx < numNodes; sIdx ++) {
                            if (!serversSkipSlotACK.get(sIdx)) {
                                isACKComplete = false;
                                break;
                            }
                        }
                        // STEP THREE: unblock when all acks received,
                        if (isACKComplete) {
                            print("Skip Slots Completes.", MASTER_LOG_HEADER); 
                            skipSlotsSema.release();
                        }
                    }
                    /* Exit */
                    if (title.equals(EXIT_TITLE)) {
                        return ;
                    }

                } catch (IOException e) {
                    ;
                } finally { 
                    try {
                        if (socket != null) socket.close(); 
                    } catch (IOException e) { ; }
                }
            }
        }
    }

    public static void main(String [] args) throws IOException, InterruptedException {
        Scanner scan = new Scanner(System.in);
        int clientIndex, nodeIndex;
        // server socket of master
        localhost = InetAddress.getLocalHost();
        final ServerSocket listener = new ServerSocket(MASTER_PORT, 0,
                localhost);
        listener.setReuseAddress(true);

        masterListener = listener;
        logfilename = Master_LOG_FILENAME;
        log = new PrintStream (new File(logfilename));
        original = new PrintStream(System.out);
        writer = new PrintWriter(RESULT_FILENAME);
        System.setOut(log);
        System.setErr(log);

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
                    // create a thread dealing with the normal messages
                    MasterRoutineExecutor masterRoutineExecutor = new MasterRoutineExecutor ();
                    masterRoutineExecutor.start(); 
                    // STEP ONE: SET UP SERVERS AND CLIENTS PROCESSES
                    setupSema = new Semaphore (0, true);
                    serversSetup = new ArrayList<Boolean> ();
                    for (nodeIndex = 0; nodeIndex < numNodes; nodeIndex ++) {
                        serversSetup.add(false);
                    }
                    clientsSetup = new ArrayList<Boolean> ();
                    for (clientIndex = 0; clientIndex < numClients; clientIndex ++) {
                        clientsSetup.add(false);
                    }
                    serverProcesses = new Process [numNodes];
                    clientProcesses = new Process [numClients];

                    // PROCESSES CREATION
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
                    setupSema.acquire();

                    // STEP TWO: ELECT A NEW LEADER
                    // we use server 0 to carry leader
                    leaderID = 0;
                    ackLeaderSema = new Semaphore (0, true);
                    serversLeaderACK = new ArrayList<Boolean> ();
                    for (nodeIndex = 0; nodeIndex < numNodes; nodeIndex ++) {
                        serversLeaderACK.add(false);
                    }
                    clientsLeaderACK = new ArrayList<Boolean> ();
                    for (clientIndex = 0; clientIndex < numClients; clientIndex ++) {
                        clientsLeaderACK.add(false);
                    }
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
                    ackLeaderSema.acquire();

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
                    // send message to client asking for chat log
                    port = CLIENT_PORT_BASE + clientIndex; 
                    String tmp_message = String.format(MESSAGE, MASTER_TYPE,
                            0, CLIENT_TYPE, clientIndex, PRINT_CHAT_LOG_TITLE, EMPTY_CONTENT);
                    send (localhost, port, tmp_message, MASTER_LOG_HEADER);
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
                    checkAllClear();
                    break;
                case "crashServer":
                    nodeIndex = Integer.parseInt(inputLine[1]);
                    /*
                     * Immediately crash the server specified by nodeIndex
                     */
                    // ======================================================
                    // We directly kill that process
                    if(serverProcesses[nodeIndex] != null) {
                        serverProcesses[nodeIndex].destroy();
                        serverProcesses[nodeIndex] = null;
                    }
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
                    checkAllClear();
                    // STEP ONE: initialize semaphore and cached acks array
                    skipSlotsSema = new Semaphore(0, true);
                    serversSkipSlotACK = new ArrayList<Boolean> ();
                    for (nodeIndex = 0; nodeIndex < numNodes; nodeIndex ++) {
                        serversSkipSlotACK.add(false);
                    }
                    // STEP TWO: send skipSlots instruction to all replicas
                    for (nodeIndex = 0; nodeIndex < numNodes; nodeIndex ++) {
                        port = SERVER_PORT_BASE + nodeIndex;
                        String SkipSlotMessage = String.format(MESSAGE,
                                MASTER_TYPE, 0, SERVER_TYPE, nodeIndex,
                                SKIP_SLOT_TITLE, Integer.toString(amountToSkip));
                        send(localhost, port, SkipSlotMessage, MASTER_LOG_HEADER);
                    }
                    // STEP THREE: block until receving all acks
                    skipSlotsSema.acquire();
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
        /* Ask all clients and servers to terminate, as well as its listenning
         * thread */
        checkAllClear();
        String exitMessage = String.format(MESSAGE, MASTER_TYPE, 0,
                MASTER_TYPE, 0, EXIT_TITLE, EMPTY_CONTENT);
        send(localhost, MASTER_PORT, exitMessage, MASTER_LOG_HEADER);
        if (clientProcesses != null) {
            for (clientIndex = 0; clientIndex < clientProcesses.length; clientIndex ++) {
                if (clientProcesses[clientIndex] != null) {
                    int port = CLIENT_PORT_BASE + clientIndex;
                    exitMessage = String.format(MESSAGE, MASTER_TYPE, 0,
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
                    exitMessage = String.format(MESSAGE, MASTER_TYPE, 0,
                            SERVER_TYPE, nodeIndex, EXIT_TITLE, EMPTY_CONTENT);
                    send(localhost, port, exitMessage, MASTER_LOG_HEADER);
                }
            }
        }
        listener.close();
    }
}
