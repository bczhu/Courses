/*##############################################################
## MODULE: Protocol.java
## VERSION: 1.0 
## SINCE: 2014-03-31
## AUTHOR: ##         JIMMY LIN (xl5224) - JimmyLin@utexas.edu  
## ## DESCRIPTION: 
##      
################################################################# 
## Edited by MacVim
## Class Info auto-generated by Snippet 
################################################################*/

/* Protocol configuration */
interface Protocol {
    //  Manually specify the base of client listener port and server
    //  listener port.
    final static int CLIENT_PORT_BASE = 8500;
    final static int SERVER_PORT_BASE = 8400; 
    final static int MASTER_PORT = 8200;

    // Manually specify the format of differnt type of message
    final static String MESSAGE_SEP = ",";
    final static String MESSAGE = "%s" + MESSAGE_SEP + "%d" + MESSAGE_SEP
        +"%s" + MESSAGE_SEP +"%d" + MESSAGE_SEP +"%s" + MESSAGE_SEP +"%s";
    final static String OUTPUT_MESSAGE = "%d %d: %s";

    // Macros for TITLE
    final static String EXIT_TITLE = "EXIT";
    final static String START_ACK_TITLE = "STARTUP_ACK";
    final static String SEND_MESSAGE_TITLE = "SENT_MESSAGE";
    final static String REQUEST_TITLE = "REQUEST";
    final static String PROPOSE_TITLE = "PROPOSAL";
    final static String DECISION_TITLE = "DECISION";

    final static String RESPONSE_TITLE = "RESPONSE";
    final static String ADOPTED_TITLE = "ADPTED";
    final static String PREEMPTED_TITLE = "PREEMPTED";
    final static String P1A_TITLE = "P1A";
    final static String P2A_TITLE = "P2A";
    final static String P1B_TITLE = "P1B";
    final static String P2B_TITLE = "P2B";
    final static String PRINT_CHAT_LOG_TITLE = "PRINT_CHAT_LOG";

    // Macros for CONTENT
    final static String COMMAND_SEP = "/c";
    final static String COMMAND = "%d" + COMMAND_SEP + "%d" + COMMAND_SEP + "%s";

    final static String EMPTY_CONTENT = "NULL";
    final static String CONTENT_SEP = ";";
    final static String PROPOSAL_CONTENT = "%d" + CONTENT_SEP + "%s";
    final static String RESPONSE_CONTENT = "%d" + CONTENT_SEP + "%d" +
        CONTENT_SEP + "%d"+ CONTENT_SEP + "%s";
    final static String ADOPTED_CONTENT = "%d" + CONTENT_SEP + "%s";
    final static String PREEMPTED_CONTENT = "%d";
    final static String DECISION_CONTENT = "%d" + CONTENT_SEP + "%s";

    final static String ACCEPTED_SEP = "/a";
    final static String PVALUE_SEP = "/p";
    final static String PVALUE_CONTENT = "%d" + PVALUE_SEP + "%d" + PVALUE_SEP + "%s";
    final static String P1A_CONTENT = "%d" + CONTENT_SEP + "%d";
    final static String P1B_CONTENT = "%d" + CONTENT_SEP + "%d" + CONTENT_SEP + "%s";
    final static String P2A_CONTENT = "%d" + CONTENT_SEP + "%s";
    final static String P2B_CONTENT = "%d" + CONTENT_SEP + "%d";

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
}
