/**##############################################################
## MODULE: msh.c
## VERSION: 1.0 
## SINCE: 2013-09-14
## AUTHOR: 
##     Jimmy Lin (xl5224, loginID: jimmylin) - JimmyLin@utexas.edu  
##     Bochao Zhan (bz2892, loginID: )- bzhan927@gmail.com
## DESCRIPTION: 
##   A mini shell program with more complex job control
## 
#################################################################
## Edited by MacVim
## Class Info auto-generated by Snippet 
################################################################*/

/* 
 * msh - A mini shell program with job control
 * 
 * <Put your name and login ID here>
 */
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <ctype.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <errno.h>
#include "util.h"
#include "jobs.h"


/* Global variables */
int verbose = 0;            /* if true, print additional output */

extern char **environ;      /* defined in libc */
static char prompt[] = "msh> ";    /* command line prompt (DO NOT CHANGE) */
static struct job_t jobs[MAXJOBS]; /* The job list */
int MAX_NUM_ARGS = 10;
sigset_t sig_child;
/* End global variables */


/* Function prototypes */

/* Here are the functions that you will implement */
void eval(char *cmdline);
int builtin_cmd(char **argv);
void do_bgfg(char **argv);
void waitfg(pid_t pid);

void sigchld_handler(int sig);
void sigtstp_handler(int sig);
void sigint_handler(int sig);

/* Here are helper routines that we've provided for you */
void usage(void);
void sigquit_handler(int sig);



/*
 * main - The shell's main routine 
 */
int main(int argc, char **argv) 
{
    printf("pid: %d, pgid:%d\n", getpid(), getpgid(getpid()));
    char c;
    char cmdline[MAXLINE];
    int emit_prompt = 1; /* emit prompt (default) */

    /* Redirect stderr to stdout (so that driver will get all output
     * on the pipe connected to stdout) */
    dup2(1, 2);

    /* Parse the command line */
    while ((c = getopt(argc, argv, "hvp")) != EOF) {
        switch (c) {
        case 'h':             /* print help message */
            usage();
	    break;
        case 'v':             /* emit additional diagnostic info */
            verbose = 1;
	    break;
        case 'p':             /* don't print a prompt */
            emit_prompt = 0;  /* handy for automatic testing */
	    break;
	default:
            usage();
	}
    }

    /* Install the signal handlers */

    /* These are the ones you will need to implement */
    Signal(SIGINT,  sigint_handler);   /* ctrl-c */
    Signal(SIGTSTP, sigtstp_handler);  /* ctrl-z */
    Signal(SIGCHLD, sigchld_handler);  /* Terminated or stopped child */

    /* This one provides a clean way to kill the shell */
    Signal(SIGQUIT, sigquit_handler); 

    /* Initialize the job list */
    initjobs(jobs);
	sigemptyset (&sig_child);
	sigaddset(&sig_child, SIGCHLD);

    /* Execute the shell's read/eval loop */
    while (1) {

	/* Read command line */
	if (emit_prompt) {
	    printf("%s", prompt);
	    fflush(stdout);
	}
	if ((fgets(cmdline, MAXLINE, stdin) == NULL) && ferror(stdin))
	    app_error("fgets error");
	if (feof(stdin)) { /* End of file (ctrl-d) */
	    fflush(stdout);
	    exit(0);
	}

	/* Evaluate the command line */
	eval(cmdline);
	fflush(stdout);
	fflush(stdout);
    } 

    exit(0); /* control never reaches here */
}
  
/* 
 * eval - Evaluate the command line that the user has just typed in
 * 
 * If the user has requested a built-in command (quit, jobs, bg or fg)
 * then execute it immediately. Otherwise, fork a child process and
 * run the job in the context of the child. If the job is running in
 * the foreground, wait for it to terminate and then return.  Note:
 * each child process must have a unique process group ID so that our
 * background children don't receive SIGINT (SIGTSTP) from the kernel
 * when we type ctrl-c (ctrl-z) at the keyboard.  
*/
void eval(char *cmdline) 
{
    int foreground;
    char * ampersand;
    // Examine the bg/fg indicator: ampersand
    if ((ampersand = strchr(cmdline, '&')) == NULL) {
        foreground = 1;
    } else {
        foreground = 0;
        *ampersand = 0;
    }
    // Initialiization of execution information
    char * args[MAX_NUM_ARGS];
    // Set null to all element of args
    int j;
    for (j = 0; j < MAX_NUM_ARGS; j ++) 
        args[j] = NULL;

    // configure the arguments
    int i = 0;
    char * delimiter = " \n"; // separator between command and arguments
    args[i++] = strtok(cmdline, delimiter); // command or file name
    char * pch;
    while ((pch = strtok(NULL, delimiter)) != NULL) {
        // Exception handling: number limit of arguments
        if (!(i < MAX_NUM_ARGS)) {
            fprintf(stderr, "Too many arguments..");
            break;
        }
        args[i++] = pch;
        // printf("%s\n", pch);
    }

    // Inspect whether it is a built-in command
    if (((pch = strtok(cmdline, delimiter)) != NULL)&&(!builtin_cmd(args))) {
        // it is a executable file
        sigprocmask(SIG_BLOCK, &sig_child, NULL);
        pid_t child = fork();

        if (child == 0) {
            // setpgid with exception handling
            if (setpgid(0, 0)) {
                printf("Setpgid Failure.\n");
                exit(-2);
            }
            // unblock the SIGCHLD signal
            sigprocmask(SIG_UNBLOCK, &sig_child, NULL);
            // execution
            if (execvp(args[0], args) == -1) {
                printf("Wrong usage of msh.\n");
                exit(-1);
            }
            // normal exit after execution
            exit(1);
        } else {
            if (foreground) {
                // add a new job to job list
                addjob(jobs, child, FG, cmdline);
                sigprocmask(SIG_UNBLOCK, &sig_child, NULL);
                printf(" Job [%d] (%d) {%d} FG %s\n", pid2jid(jobs, child), 
                        child, getpgid(child), jobs[i].cmdline);
            } else {
                // add a new job to job list
                addjob(jobs, child, BG, cmdline);
                sigprocmask(SIG_UNBLOCK, &sig_child, NULL);
                printf(" Job [%d] (%d) {%d} BG %s\n", pid2jid(jobs, child), 
                        child, getpgid(child), jobs[i].cmdline);
            }
        }
    }
    return;
}


/* 
 * builtin_cmd - If the user has typed a built-in command then execute
 *    it immediately.  
 * Return 1 if a builtin command was executed; return 0
 * if the argument passed in is *not* a builtin command.
 */
int builtin_cmd(char **argv) 
{
    // The quit command terminates the shell.
    if (strcmp(*argv, "quit") == 0) {
        exit(1); // user-specified quit 
    }

    // The jobs command lists all background jobs.
    if (strcmp(*argv, "jobs") == 0) {
        // count the number of background jobs.
        int numBG = 0, i;
        for (i = 0; i < MAXJOBS; i ++) {
            if (jobs[i].state == BG)
                numBG ++;
        }
        // title display
        if (!numBG) 
            printf("\nThere are no background jobs.\n");
        else
            printf("\nAll background jobs are listed as follows: \n");
        // display the specifics of bg jobs to screen
        for (i = 0; i < MAXJOBS; i ++) {
            if (jobs[i].state == BG)
                printf(" Job [%d] (%d) {%d} BG %s\n", jobs[i].jid, jobs[i].pid,
                        getpgid(jobs[i].pid), jobs[i].cmdline);
        }
        return 1;
    }

    if (strcmp(*argv, "bg") == 0 || strcmp(*argv, "fg") == 0) {
        do_bgfg(argv);     
        return 1;
    }

    return 0; // not a built-in command
}

/* 
 * do_bgfg - Execute the builtin bg and fg commands
 * FIXME: exception handling for jid with non-numeric character
 */
void do_bgfg(char **argv) 
{
    char * job_argv = *(argv+1);
    pid_t pid;
    int jid;
    struct job_t *job;
    if (*job_argv == '%') { // this is jid input
        jid = atoi(strtok(job_argv, "%"));
        job = getjobjid(jobs, jid);
        if (job == NULL) {
            printf("Error Job id input.\n");
            exit(-1);
        }
        pid = job->pid;
        printf("jid: %d \n",jid);
    } else { // this is pid input
        pid = atoi(job_argv);
        job = getjobpid(jobs, pid);
        printf("pid: %d \n",pid);
    }

    // This bg command starts job in the background.
    if (strcmp(*argv, "bg") == 0) {
        // send signal to pid and continue the running
        if (!kill(pid, SIGCONT)) {
            // put to background: set process group id to its own pid
            job->state = BG;
        }
    } else  { // This fg command starts job in the foreground.
        // send signal to pid and continue the running
        if (!kill(pid, SIGCONT)) {
            job->state = FG;
            // put to foreground: set process group id to its own pid
            setpgid(pid, getpgrp());
        }
    }
    return;
}

/* 
 * waitfg - Block until process pid is no longer the foreground process
 */
void waitfg(pid_t pid)
{
    int status;
    waitpid(pid, &status, 0);
    return;
}

/*****************
 * Signal handlers
 *****************/

/* 
 * sigchld_handler - The kernel sends a SIGCHLD to the shell whenever
 *     a child job terminates (becomes a zombie), or stops because it
 *     received a SIGSTOP or SIGTSTP signal. The handler reaps all
 *     available zombie children, but doesn't wait for any other
 *     currently running children to terminate.  
 */
void sigchld_handler(int sig) 
{
	int pid, status, exit_value;
    while((pid = waitpid(-1, &status, WNOHANG)) > 0){
        exit_value = WEXITSTATUS(status);
        printf("A Child Process, Which PID= %d, Is Terminated With EXIT Status= %d\n", pid, exit_value);
    }
    printf("%s", prompt);
    fflush(stdout);

    return;
}

/* 
 * sigint_handler - The kernel sends a SIGINT to the shell whenver the
 *    user types ctrl-c at the keyboard.  Catch it and send it along
 *    to the foreground job.  
 */
void sigint_handler(int sig) 
{
    int i;
    // print information to screen
    for (i = 0; i < MAXJOBS; i ++) {
        if (jobs[i].state == FG) {
            // send the signal SIGINT to foreground job
            if (kill(jobs[i].pid, SIGINT)) {
                printf("\n Signal Delivery Failure. \n");  
                exit(-3);
            }
            printf("\n Job [%d] (%d) terminated by signal %d \n", 
                    jobs[i].jid, jobs[i].pid, SIGINT);
            deletejob(jobs, jobs[i].pid);
        }
    }
    return;
}

/*
 * sigtstp_handler - The kernel sends a SIGTSTP to the shell whenever
 *     the user types ctrl-z at the keyboard. Catch it and suspend the
 *     foreground job by sending it a SIGTSTP.  
 */
void sigtstp_handler(int sig) 
{
    int i;
    // print information to screen
    for (i = 0; i < MAXJOBS; i ++) {
        if (jobs[i].state == FG) {
            // send the signal SIGINT to foreground job
            if (kill(jobs[i].pid, SIGTSTP)) {
                printf("\n Signal Delivery Failure. \n");  
                exit(-3);
            }
            printf("\n Job [%d] (%d) stopped by signal %d \n", 
                    jobs[i].jid, jobs[i].pid, SIGTSTP);
        }
    }
	fflush(stdout);
    printf("%s", prompt);
	fflush(stdout);
    return;
}

/*********************
 * End signal handlers
 *********************/



/***********************
 * Other helper routines
 ***********************/

/*
 * usage - print a help message
 */
void usage(void) 
{
    printf("Usage: shell [-hvp]\n");
    printf("   -h   print this message\n");
    printf("   -v   print additional diagnostic information\n");
    printf("   -p   do not emit a command prompt\n");
    exit(1);
}

/*
 * sigquit_handler - The driver program can gracefully terminate the
 *    child shell by sending it a SIGQUIT signal.
 */
void sigquit_handler(int sig) 
{
    printf("Terminating after receipt of SIGQUIT signal\n");
    exit(1);
}
