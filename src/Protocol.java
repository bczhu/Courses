/*##############################################################
## MODULE: Protocol.java
## VERSION: 2.0 
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

/* Protocol configuration */
interface Protocol {
    //  Manually specify the base of client listener port and server
    //  listener port.
    final static int CLIENT_PORT_BASE = 8505;
    final static int SERVER_PORT_BASE = 8510; 
    final static int MASTER_PORT = 8515;

    /* Manually specify the format of differnt type of message */
    final static String MESSAGE_SEP = ",";
    final static String MESSAGE = "%s" + MESSAGE_SEP + "%d" + MESSAGE_SEP
        +"%s" + MESSAGE_SEP +"%d" + MESSAGE_SEP +"%s" + MESSAGE_SEP +"%s";
    final static String OUTPUT_MESSAGE = "%d %d: %s";

    /* MACROS FOR TITLE */
    final static String EXIT_TITLE = "EXIT";
    final static String START_ACK_TITLE = "STARTUP_ACK";
    final static String REQUEST_TITLE = "REQUEST";
    final static String SEND_MESSAGE_TITLE = "SENT_MESSAGE";
    final static String PRINT_CHAT_LOG_TITLE = "PRINT_CHAT_LOG";
    final static String SKIP_SLOT_TITLE = "SKIP_SLOT";
    final static String SKIP_SLOT_ACK_TITLE = "SKIP_SLOT_ACK";
    final static String TIME_BOMB_TITLE = "TIME_BOMB";
    final static String CHECK_CLEAR_TITLE = "CHECK_CLEAR";
    final static String CHECK_CLEAR_ACK_TITLE = "CHECK_CLEAR_ACK";
    final static String I_WANNA_RECOVER_TITLE = "I_WANNA_RECOVER";
    final static String HELP_YOU_RECOVER_TITLE = "YOU_CAN_RECOVER";
    final static String HERE_IS_CHAT_LOG_TITLE = "HERE_IS_CHAT_LOG";

    final static String LEADER_REQUEST_TITLE = "LEADER COMES OUT";
    final static String LEADER_ACK_TITLE = "I CONFIRM HE IS LEADER";
    final static String LEADER_PROPOSAL_TITLE = "I WANNA BE THE LEADER";
    final static String LEADER_PROPOSAL_ACCEPT_TITLE = "ACCEPT LEADER PROPOSAL";
    final static String LEADER_PROPOSAL_REJECT_TITLE = "REJECT LEADER PROPOSAL";

    /* PAXOS TITLE */
    final static String RESPONSE_TITLE = "RESPONSE";
    final static String ADOPTED_TITLE = "ADOPTED";
    final static String PREEMPTED_TITLE = "PREEMPTED";
    final static String P1A_TITLE = "P1A";
    final static String P2A_TITLE = "P2A";
    final static String P1B_TITLE = "P1B";
    final static String P2B_TITLE = "P2B";
    final static String PROPOSE_TITLE = "PROPOSAL";
    final static String DECISION_TITLE = "DECISION";

    // Macros for CONTENT
    final static String COMMAND_SEP = "</c>";
    final static String COMMAND = "%d" + COMMAND_SEP + "%d" + COMMAND_SEP + "%s";

    final static String EMPTY_CONTENT = "NULL";
    final static String CONTENT_SEP = "<;>";
    final static String PROPOSAL_CONTENT = "%d" + CONTENT_SEP + "%s";
    final static String RESPONSE_CONTENT = "%d" + CONTENT_SEP + "%d" +
        CONTENT_SEP + "%d"+ CONTENT_SEP + "%s";
    final static String ADOPTED_CONTENT = "%d" + CONTENT_SEP + "%s";
    final static String PREEMPTED_CONTENT = "%d" + CONTENT_SEP + "%d";
    final static String DECISION_CONTENT = "%d" + CONTENT_SEP + "%s";

    final static String ACCEPTED_SEP = "</a>";
    final static String PVALUE_SEP = "</p>";
    final static String PVALUE_CONTENT = "%d" + PVALUE_SEP + "%d" + PVALUE_SEP + "%s";
    final static String P1A_CONTENT = "%d" + CONTENT_SEP + "%d";
    // Acceptor ID, requested ballot number, promised ballot number, promised leader ID, accepted values
    final static String P1B_CONTENT = "%d" + CONTENT_SEP + "%d" + CONTENT_SEP + "%d" + CONTENT_SEP + "%d" + CONTENT_SEP + "%s";
    final static String P2A_CONTENT = "%d" + CONTENT_SEP + "%s";
    // Acceptor ID, requested ballot number, requested slot number, promised ballot number, promised leader ID
    final static String P2B_CONTENT = "%d" + CONTENT_SEP + "%d" + CONTENT_SEP + "%d" + CONTENT_SEP + "%d" + CONTENT_SEP + "%d";

    final static String MAP_SEP = "</mapto>";
    final static String DECISION_SEP = "</decn>";
    final static String RECOVERY_INFO_SEP = "</recov>";
    final static String CHAT_PIECE_SEP = "</chatsep>";

    final static String SKIPPED_MARKER = "</skiped>";

    // message design
    final static int SENDER_TYPE_IDX = 0;
    final static int SENDER_INDEX_IDX = 1;
    final static int RECEIVER_TYPE_IDX = 2;
    final static int RECEIVER_INDEX_IDX = 3;
    final static int TITLE_IDX = 4;
    final static int CONTENT_IDX = 5;

    final static String SERVER_TYPE = "SERVER";
    final static String CLIENT_TYPE = "CLIENT";
    final static String MASTER_TYPE = "MASTER";
    final static String LEADER_TYPE = "LEADER";
    final static String ACCEPTOR_TYPE = "ACCEPTOR";

    // HEARTBEAT CONFIGURATION
    final static int HB_INTERVAL = 20; // unit: milli-seconds
    final static int HB_TIMEOUT = 50; // unit: milli-seconds
    final static int HB_CHECK_INTERVAL = 10;
    final static int HB_INITIAL_WAIT = 50;
    final static String HEARTBEAT_TITLE = "THIS IS A HEART BEAT!";

    // LEADE ELECTION CONFIGURATION
    final static int LEADER_PROPOSAL_TIMEOUT = 1000;

    // Interrupt type
    final static int NO_REASON = -1;
    final static int TIMEBOMB_INTERRUPT = 0;
    final static int LEADER_FAILURE_INTERRUPT = 1;
    final static int LEADER_CHECK_INTERRUPT = 2;
    final static int EXIT_INTERRUPT = 3;
}
