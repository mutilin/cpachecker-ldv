#!/usr/bin/python
"""
CPAchecker is a tool for configurable software verification.
This file is part of CPAchecker.

Copyright (C) 2007-2014  Dirk Beyer
All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.


CPAchecker web page:
  http://cpachecker.sosy-lab.org
"""

# prepare for Python 3
from __future__ import absolute_import, print_function, unicode_literals

import sys
sys.dont_write_bytecode = True # prevent creation of .pyc files

import argparse
import logging
import benchmark.runexecutor
import signal

_BYTE_FACTOR = 1000 # byte in kilobyte

def main(argv=None):
    if argv is None:
        argv = sys.argv

    # parse options
    parser = argparse.ArgumentParser(description=
        "Run a command with resource limits and measurements.")
    parser.add_argument("args", nargs="+", metavar="ARG",
                        help='command line to run (prefix with "--" to ensure all arguments are treated correctly)')
    parser.add_argument("--output", default="output.log", metavar="FILE",
                        help="file name for file with command output")
    parser.add_argument("--maxOutputSize", type=int, metavar="BYTES",
                        help="approximate size of command output after which it will be truncated")
    parser.add_argument("--memlimit", type=int, metavar="BYTES",
                        help="memory limit in bytes")
    parser.add_argument("--timelimit", type=int, metavar="SECONDS",
                        help="CPU time limit in seconds")
    parser.add_argument("--softtimelimit", type=int, metavar="SECONDS",
                        help='"soft" CPU time limit in seconds')
    parser.add_argument("--dir", metavar="DIR",
                        help="working directory for executing the command (default is current directory)")
    verbosity = parser.add_mutually_exclusive_group()
    verbosity.add_argument("--debug", action="store_true",
                           help="Show debug output")
    verbosity.add_argument("--quiet", action="store_true",
                           help="Show only warnings")
    options = parser.parse_args(argv[1:])

    # for usage inside the verifier cloud, there is a special mode
    # where the first and only command-line argument is a serialized dict
    # with additional options
    env = {}
    if len(options.args) == 1 and options.args[0].startswith("{"):
        data = eval(options.args[0])
        options.args = data["args"]
        env = data.get("env", {})
        options.debug = data.get("debug", options.debug)
        if "maxLogfileSize" in data:
            options.maxOutputSize = data["maxLogfileSize"] * _BYTE_FACTOR * _BYTE_FACTOR # MB to bytes

    # setup logging
    logLevel = logging.INFO
    if options.debug:
        logLevel = logging.DEBUG
    elif options.quiet:
        logLevel = logging.WARNING
    logging.basicConfig(format="%(asctime)s - %(levelname)s - %(message)s",
                        level=logLevel)

    executor = benchmark.runexecutor.RunExecutor()

    # ensure that process gets killed on interrupt/kill signal
    def signal_handler_kill(signum, frame):
        executor.kill()
    signal.signal(signal.SIGTERM, signal_handler_kill)
    signal.signal(signal.SIGINT,  signal_handler_kill)

    logging.info('Starting command ' + ' '.join(options.args))
    logging.info('Writing output to ' + options.output)

    # actual run execution
    (wallTime, cpuTime, memUsage, exitCode, energy) = \
        executor.executeRun(args=options.args,
                            outputFileName=options.output,
                            hardtimelimit=options.timelimit,
                            softtimelimit=options.softtimelimit,
                            memlimit=options.memlimit,
                            environments=env,
                            runningDir=options.dir,
                            maxLogfileSize=options.maxOutputSize)

    # exitCode is a special number:
    # It is a 16bit int of which the lowest 7 bit are the signal number,
    # and the high byte is the real exit code of the process (here 0).
    returnValue = exitCode / 256
    exitSignal = exitCode % 128

    # output results
    print("exitcode=" + str(exitCode))
    if (exitSignal == 0) or (returnValue != 0):
        print("returnvalue=" + str(returnValue))
    if exitSignal != 0 :
        print("exitsignal=" + str(exitSignal))
    print("walltime=" + str(wallTime) + "s")
    print("cputime=" + str(cpuTime) + "s")
    print("memory=" + str(memUsage))
    if energy:
        print("energy=" + str(energy))

if __name__ == "__main__":
    sys.exit(main())
