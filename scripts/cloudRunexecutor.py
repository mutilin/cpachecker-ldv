#!/usr/bin/python

# prepare for Python 3
from __future__ import absolute_import, print_function, unicode_literals

import os
import signal
import sys
import logging
import benchmark.runexecutor as runexecutor
from benchmark.runexecutor import RunExecutor

MEMLIMIT = runexecutor.MEMLIMIT
TIMELIMIT = runexecutor.TIMELIMIT
CORELIMIT = runexecutor.CORELIMIT

def main(argv=None):
    if argv is None:
        argv = sys.argv

        #sys.stderr.write(str(argv)+"\n")

    if len(argv) >= 5 and len(argv) <=6:

<<<<<<< HEAD
        rlimits={}

        #"  " -> replace with " " and " " -> split at this position
        argStr = argv[1]
        args = []
        tmp=""
        lastWasWhiteSpace = False
        for c in argStr:
            if(lastWasWhiteSpace):
                if(c != " "):
                    #split
                    args.append(tmp)
                    lastWasWhiteSpace = False
                    tmp="" + c
                else:
                    tmp += " "
                    lastWasWhiteSpace = False
            else:
                if(c == " "):
                    lastWasWhiteSpace = True
                else:
                    tmp += c
                    lastWasWhiteSpace = False

        args.append(tmp)

        if(not (argv[2]=="-1" or argv[2]=="None")):
=======

        rlimits={}

        data = eval(argv[1]) # arg[1] is a string-representation of a data-structure
        args = data.get("args", [])
        env = data.get("env", {})
        debugEnabled = data.get("debug", False)

        if debugEnabled:
            logging.basicConfig(format="%(asctime)s - %(levelname)s - %(message)s",
                            level=logging.DEBUG)
        else:
            logging.basicConfig(format="%(asctime)s - %(levelname)s - %(message)s")

        if not (argv[2]=="-1" or argv[2]=="None"):
>>>>>>> master
            rlimits[MEMLIMIT] = int(argv[2])
        rlimits[TIMELIMIT] = int(argv[3])
        outputFileName = argv[4]
        if(len(argv) == 6):
             rlimits[CORELIMIT] = int(argv[5])

        global runExecutor
        runExecutor = RunExecutor()

        logging.debug("runExecutor.executeRun() started.")

        (wallTime, cpuTime, memUsage, returnvalue, output) = \
            runExecutor.executeRun(args, rlimits, outputFileName, environments=env);

        logging.debug("runExecutor.executeRun() ended.")

        print("Walltime: " + str(wallTime))
        print("CpuTime: " + str(cpuTime))
        print("MemoryUsage: " + str(memUsage))
        print("Returnvalue: " + str(returnvalue))

        return returnvalue

    else:
        sys.exit("Wrong number of arguments, expected exactly 4 or 5: <command> <memlimit in MB> <timelimit in s> <output file name> <core limit(optional)>")

def signal_handler_kill_script(signum, frame):
<<<<<<< HEAD
    runexecutor.killAllProcesses()
=======
    runExecutor.kill()
>>>>>>> master

if __name__ == "__main__":

    signal.signal(signal.SIGTERM, signal_handler_kill_script)

    main()
