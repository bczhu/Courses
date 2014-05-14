/*##############################################################
## MODULE: Leader.java
## VERSION: 1.0 
## SINCE: 2014-04-02
## AUTHOR: 
##     JIMMY LIN (xl5224) - JimmyLin@utexas.edu  
##     CALVIN SZETO - Szeto.calvin@gmail.com
## DESCRIPTION: 
##      
#################################################################
## Edited by MacVim
## Class Info auto-generated by Snippet 
################################################################*/

import java.util.concurrent.LinkedBlockingQueue;
import java.util.HashMap;
import java.util.ArrayList;
import java.net.InetAddress;
import java.io.IOException;

class Leader extends Util implements Runnable{
    private LinkedBlockingQueue<String> queue = null;
    // ballotNumber: current ballot number
    private int ballot_num;
    // active: active or passive?
    private boolean isActive = false;
    // proposals: proposals so far
    private HashMap<Integer, String> proposals;

    private int timeBombMessages;

    private int serverID;
    private Thread replica;
    private int numServers;
    private String logHeader;

    // for heartbeat implementation
    private Thread heartbeat;

    private InetAddress localhost;

    private HashMap<Integer, LinkedBlockingQueue<String>> scoutQueues;
    private HashMap<String, LinkedBlockingQueue<String>> commanderQueues;

    public Leader (LinkedBlockingQueue<String> queue, int id, int numServers,
            InetAddress localhost, Thread replica) { 
        if (id == 0)
            isActive = true;

        this.queue = queue;
        this.ballot_num = 0;
        this.serverID = id;
        this.numServers = numServers;
        this.logHeader = String.format(LEADER_LOG_HEADER, id);
        this.localhost = localhost;
        this.timeBombMessages = -1;
        this.replica = replica;

        proposals = new HashMap<Integer, String>();
        scoutQueues = new HashMap<Integer, LinkedBlockingQueue<String>>();
        commanderQueues = new HashMap<String, LinkedBlockingQueue<String>>();

        /* Definition of heartbeat thread */
        final Integer ServerID = serverID;
        final Integer NumServers = numServers;
        final InetAddress Localhost = localhost;
        heartbeat = new Thread (new Runnable () {
            // change the alive indicator to stop the heartbeat, that is, kill
            // this thread
            public boolean alive = true;
            public void run () {
                while (this.alive) {
                    for (int serverIndex = 0; serverIndex < NumServers; serverIndex++) {
                        // ignore sending heartbeat to the server carrying it,
                        // avoid wastage
                        if (serverIndex == serverID) continue; 
                        String hbMsg = String.format(MESSAGE, LEADER_TYPE,
                            ServerID, SERVER_TYPE, serverIndex,
                            HEARTBEAT_TITLE, EMPTY_CONTENT);
                        int port = SERVER_PORT_BASE + serverIndex;
                        send (Localhost, port, hbMsg, logHeader);
                    }
                    try {
                        Thread.sleep(HB_INTERVAL);
                    } catch (InterruptedException e) {
                        ;
                    }
                }
            }
        });
    }

    public void run() {
        heartbeat.start();
        // Spawn a Scout for the current ballot number
        LinkedBlockingQueue<String> queueScout = new LinkedBlockingQueue<String>();
        (new Thread(new Scout(queueScout, serverID, numServers, ballot_num, localhost, 0))).start(); 
        scoutQueues.put(ballot_num, queueScout);
        while (true) {
            // receive messages from queue
            String msg = null;
            try {
                msg = queue.take();
            }  catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (msg == null) continue;
            printReceivedMessage(msg, logHeader);

            String[] msgParts = msg.split(MESSAGE_SEP);
            String title = msgParts[TITLE_IDX];
            String content = msgParts[CONTENT_IDX];
            String[] contentParts = content.split(CONTENT_SEP); 
            if (title.equals(P1B_TITLE)) {
                try {
                    int tmp_b = Integer.parseInt(contentParts[1]);
                    scoutQueues.get(tmp_b).put(msg);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if (title.equals(P2B_TITLE)) {
                try{
                    int tmp_b = Integer.parseInt(contentParts[1]);
                    int tmp_s = Integer.parseInt(contentParts[2]);
                    String commanderID = tmp_s + " " + tmp_b;
                    commanderQueues.get(commanderID).put(msg);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if (title.equals(PROPOSE_TITLE)) {
                int s = Integer.parseInt(contentParts[0]);
                String p = contentParts[1];
                // if Leader hasn't proposed something for this slot already
                if(proposals.get(s) == null) {
                    // add this proposal to list of proposals so far
                    proposals.put(s,p);
                    // if the Leader is active
                    if(isActive) {
                        String commanderID = s + " " + ballot_num;
                        // spawn a Commander for this ballot
                        int messagesUntilCrash = checkTimeBomb();
                        LinkedBlockingQueue<String> queueCommander = new LinkedBlockingQueue<String>();
                        Thread thread = new Thread(new Commander(queueCommander, serverID,  numServers, numServers, String.format(PVALUE_CONTENT, ballot_num, s, p), localhost, messagesUntilCrash));
                        thread.start();
                        if(messagesUntilCrash > 0) {
                            try {
                                thread.join();
                            }  catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            // Inform the replica
                            Server.interruptReason = TIMEBOMB_INTERRUPT;
                            replica.interrupt();
                            return;
                        }
                        commanderQueues.put(commanderID, queueCommander);
                    }
                }
            } else if (title.equals(ADOPTED_TITLE)) {
                int b = Integer.parseInt(contentParts[0]);
                String[] pvals = contentParts[1].split(ACCEPTED_SEP);
                // update proposals so far with highest ballots for each slot returned by the adopted message
                for (String newPval:pmax(pvals)) {
                    // for each proposal in the pmax
                    String[] newPvalParts = newPval.split(PVALUE_SEP);
                    int newS = Integer.parseInt(newPvalParts[1]);
                    String newP = newPvalParts[2];
                    // update proposals with that pvalue
                    proposals.put(newS, newP);
                }
                // for all proposals so far
                for (int tmp_s: proposals.keySet()) {
                    // spawn a Commander for that proposal
                    int messagesUntilCrash = checkTimeBomb();
                    LinkedBlockingQueue<String> queueCommander = new LinkedBlockingQueue<String>();
                    String commanderID = tmp_s + " " + ballot_num;
                    Thread thread = new Thread(new Commander(queueCommander, serverID, numServers, numServers, String.format(PVALUE_CONTENT, ballot_num, tmp_s, proposals.get(tmp_s)), localhost, messagesUntilCrash)); 
                    thread.start();
                    if(messagesUntilCrash > 0) {
                        try {
                            thread.join();
                        }  catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        // Inform the replica
                        Server.interruptReason = TIMEBOMB_INTERRUPT;
                        replica.interrupt();
                        return;
                    }
                    commanderQueues.put(commanderID, queueCommander);
                }
                // become Active
                isActive = true; 
            } else if (title.equals(PREEMPTED_TITLE)) {
                int b = Integer.parseInt(contentParts[0]); 
                int l = Integer.parseInt(contentParts[1]);
                // if the ballot number in the message is greater than the current ballot number
                if (b > ballot_num || l != serverID) {
                    // become Passive
                    isActive = false;
                    // update the ballot number
                    ballot_num = b + 1;
                    // spawn a scout for the new ballot number
                    int messagesUntilCrash = checkTimeBomb();
                    queueScout = new LinkedBlockingQueue<String>();
                    Thread thread = new Thread(new Scout(queueScout, serverID, numServers, ballot_num, localhost, messagesUntilCrash)); 
                    thread.start();
                    if(messagesUntilCrash > 0) {
                        try {
                            thread.join();
                        }  catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        // Inform the replica
                        Server.interruptReason = TIMEBOMB_INTERRUPT;
                        replica.interrupt();
                        return;
                    }
                    scoutQueues.put(ballot_num, queueScout);
                }
            } else if (title.equals(TIME_BOMB_TITLE)) {
                // Set message count
                timeBombMessages = Integer.parseInt(contentParts[0]);
                // If message count is 0, exit immediately
                if (timeBombMessages == 0) {
                    // Inform the replica
                    Server.interruptReason = TIMEBOMB_INTERRUPT;
                    replica.interrupt();
                    return;
                }
            }
        }
    }

    private String[] pmax (String[] pvals) {
        /*
         * pmax takes an array of pvalues and returns an array of pvalues
         * where for each slot number that exists in the original array,
         * the pvalue with the highest ballot number is added to the new array
         */
        if(pvals.length == 0)
            return new String[0];
        ArrayList<String> pmax = new ArrayList<String>();
        for(int i=0; i<pvals.length; i++) {
            String pval = pvals[i];
            // Ignore pvals that have been processed already
            if(pval != null) {
                String[] pvalParts = pval.split(PVALUE_SEP);
                if(pvalParts.length != 3)
                    continue;
                int s = Integer.parseInt(pvalParts[1]);
                int maxB = -1;
                String maxPval = null;
                // Pick the pvalue with the largest ballot number for a given slot number
                for(String tmp_pval: pvals) {
                    if(tmp_pval != null) {
                        String[] tmp_pvalParts = tmp_pval.split(PVALUE_SEP);
                        if(tmp_pvalParts.length != 3)
                            continue;
                        int tmp_b = Integer.parseInt(tmp_pvalParts[0]);
                        int tmp_s = Integer.parseInt(tmp_pvalParts[1]);
                        if(tmp_s == s) {
                            if(tmp_b > maxB) {
                                maxB = tmp_b;
                                maxPval = pval;
                            }
                            pvals[i] = null;
                        }
                    }
                }
                if (maxPval != null) pmax.add (maxPval);
            }
        }
        return (String []) pmax.toArray(new String[0]);
    }

    private int checkTimeBomb () {
        /* returns the number of messages for the scout/commander to send before crashing */
        if(timeBombMessages < 0) {
            // don't crash
            return 0;
        } else if(timeBombMessages > numServers ){
            // decrement time bomb
            timeBombMessages -= numServers;
            // don't crash
            return 0;
        } else {
            // crash after timeBomb expires
            return timeBombMessages;
        }
    }

    class Scout implements Runnable { 
        private LinkedBlockingQueue<String> queue = null;
        private int leaderID;
        // waitFor: the acceptors that the scout is still waiting for
        // 0 means still waiting, 1 means received
        private int[] waitFor;
        // pValues: the set of pValues received so far
        ArrayList<String> pvalues;

        private InetAddress localhost;
        private String logHeader;
        private int messagesUntilCrash;

        public Scout (LinkedBlockingQueue<String> queue, int leaderID, int
                numAcceptors, int ballot_num, InetAddress localhost, int
                messagesUntilCrash) {
            this.queue = queue;
            this.leaderID = leaderID;
            this.waitFor = new int[numAcceptors];
            this.pvalues = new ArrayList<String>();
            this.logHeader = String.format(SCOUT_LOG_HEADER, ballot_num);
            this.localhost = localhost;
            this.messagesUntilCrash = messagesUntilCrash;
        }

        public void run () {
            // for all acceptors
            for(int a = 0; a < waitFor.length; a++) {
                // send <p1a, leader, ballot number>
                int port = SERVER_PORT_BASE + a;
                String p1aContent = String.format(P1A_CONTENT, leaderID, ballot_num);
                String p1aMessage = String.format(MESSAGE, LEADER_TYPE,
                        leaderID, ACCEPTOR_TYPE, a, P1A_TITLE, p1aContent);
                if(send(localhost, port, p1aMessage, logHeader) == false)
                    continue;
                messagesUntilCrash--;
                if(messagesUntilCrash == 0) 
                    return;
            }
            while(true) {
                // receive messages from queue
                String msg = null;
                try {
                    msg = queue.take();
                }  catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (msg == null) continue;
                String[] msgParts = msg.split(MESSAGE_SEP);
                String title = msgParts[TITLE_IDX];
                if(title.equals(P1B_TITLE)) {
                    String[] p1bParts = msgParts[CONTENT_IDX].split(CONTENT_SEP);
                    int acceptor = Integer.parseInt(p1bParts[0]);
                    int newBallotNum = Integer.parseInt(p1bParts[2]);
                    int newBallotLeader = Integer.parseInt(p1bParts[3]);
                    // Note: there may be no pvals accepted yet
                    String pval = p1bParts.length == 5 ? p1bParts[4] : "";
                    // if message is a p1b for the same ballot number
                    if(newBallotNum == ballot_num && newBallotLeader == leaderID) {
                        // add pvalues to pValues
                        pvalues.add(pval); 
                        // remove acceptor from waitFor
                        waitFor[acceptor] = 1;
                        int numReceived = 0;
                        for(int tmp_a: waitFor)
                            if(tmp_a == 1)
                                ++numReceived;
                        // if waiting for fewer than half of all acceptors
                        if(numReceived > waitFor.length/2) {
                            // send adopted, ballot number, pValues to leader
                            int port = SERVER_PORT_BASE + leaderID;
                            String adopted_str = "";
                            for (String pvalue : pvalues) {
                                adopted_str += pvalue + ACCEPTED_SEP;
                            }
                            int endIndex = adopted_str.length()-ACCEPTED_SEP.length();
                            if (endIndex > 0)
                                adopted_str = adopted_str.substring(0, endIndex);
                            String adoptedContent = String.format(ADOPTED_CONTENT, ballot_num, adopted_str);
                            String adoptedMessage = String.format(MESSAGE, LEADER_TYPE,
                                    leaderID, LEADER_TYPE, leaderID, ADOPTED_TITLE, adoptedContent);
                            if(!send(localhost, port, adoptedMessage, logHeader))
                                continue;
                            // exit
                            return; 
                        }
                    } else {
                        // else send preempted and the higher ballot number to leader
                        int port = SERVER_PORT_BASE + leaderID;
                        String preemptedContent = String.format(PREEMPTED_CONTENT, newBallotNum, newBallotLeader);
                        String preemptedMessage = String.format(MESSAGE, LEADER_TYPE,
                                leaderID, LEADER_TYPE, leaderID, PREEMPTED_TITLE, preemptedContent);
                        if(!send(localhost, port, preemptedMessage, logHeader))
                            continue;
                        // exit
                        return;
                    }
                }
            }
        }
    }

    class Commander implements Runnable {
        private LinkedBlockingQueue<String> queue = null;
        // waitFor: the acceptors that the commander is still waiting for
        // 0 means still waiting, 1 means received
        private int[] waitFor;
        private int numServers;

        private int leaderID;

        private int ballot_num;
        private int slot_num;
        private String p;

        private String logHeader;
        private InetAddress localhost;
        private int messagesUntilCrash;

        /* Constructor */
        public Commander(LinkedBlockingQueue<String> queue, int leaderID, int numAcceptors,
                int numServers, String pval, InetAddress localhost, int messagesUntilCrash) {
            this.queue = queue;
            this.waitFor = new int[numAcceptors];
            this.leaderID = leaderID;
            this.numServers = numServers;
            String[] pvalParts = pval.split(PVALUE_SEP);
            this.ballot_num = Integer.parseInt(pvalParts[0]);
            this.slot_num = Integer.parseInt(pvalParts[1]);
            this.p = pvalParts[2];
            this.logHeader = String.format(COMMANDER_LOG_HEADER, ballot_num);
            this.localhost = localhost;
            this.messagesUntilCrash = messagesUntilCrash;
        }

        public void run() {
            // for all acceptors
            for(int a = 0; a < waitFor.length; a++) {
                // send <p2a, leader, ballot>
                int port = SERVER_PORT_BASE + a;
                String p2aContent = String.format(P2A_CONTENT, leaderID,
                     String.format(PVALUE_CONTENT, ballot_num, slot_num, p));
                String p2aMessage = String.format(MESSAGE, LEADER_TYPE,
                        leaderID, ACCEPTOR_TYPE, a, P2A_TITLE, p2aContent);
                if(!send(localhost, port, p2aMessage, logHeader))
                    continue;
                messagesUntilCrash--;
                if(messagesUntilCrash == 0) 
                    return;
            }
            while (true) {
                // receive messages from queue   
                String msg = null;
                try {
                    msg = queue.take();
                }  catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (msg == null) continue;
                String[] msgParts = msg.split(MESSAGE_SEP);
                String title = msgParts[TITLE_IDX];
                String contents = msgParts[CONTENT_IDX];
                if (title.equals(SKIP_SLOT_TITLE)) {
                    // TODO: ADD CODE FOR SKIP SLOT HERE
                    // FIXME: do we need to care about skip slot for commander
                } else if (title.equals(P2B_TITLE)) {
                    String[] p2bParts = contents.split(CONTENT_SEP);
                    int acceptor = Integer.parseInt(p2bParts[0]);
                    int newBallotNum = Integer.parseInt(p2bParts[3]);
                    int newBallotLeader = Integer.parseInt(p2bParts[4]);
                    // if message is a p2b for the same ballot number
                    if (newBallotNum == ballot_num && newBallotLeader == leaderID) {
                        // remove acceptor from waitFor
                        waitFor[acceptor] = 1;
                        int numReceived = 0;
                        for(int tmp_a: waitFor)
                            if(tmp_a == 1)
                                ++numReceived;
                        // if waiting for fewer than half of all acceptors
                        if (numReceived > waitFor.length/2) {
                            // send decision to ALL servers
                            for (int r = 0; r < numServers; r ++) {
                                int port = SERVER_PORT_BASE + r;
                                String decisionContent = String.format(DECISION_CONTENT, slot_num, p);
                                String decisionMessage = String.format(MESSAGE, LEADER_TYPE,
                                        leaderID, SERVER_TYPE, r, DECISION_TITLE, decisionContent);
                                if(!send(localhost, port, decisionMessage, logHeader))
                                    continue;
                            }
                            return;
                        }
                        // else send preempted and the higher ballot number to leader
                    } else {
                        int port = SERVER_PORT_BASE + leaderID;
                        String preemptedContent = String.format(PREEMPTED_CONTENT, newBallotNum, newBallotLeader);
                        String preemptedMessage = String.format(MESSAGE, LEADER_TYPE,
                                leaderID, LEADER_TYPE, leaderID, PREEMPTED_TITLE, preemptedContent);
                        if(!send(localhost, port, preemptedMessage, logHeader))
                            continue;
                        // exit
                        return;
                    }
                }
            }
        }
    }
}
