#!/usr/bin/env python

"""
CPAchecker is a tool for configurable software verification.
This file is part of CPAchecker.

Copyright (C) 2007-2013  Dirk Beyer
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


try:
  import Queue
except ImportError: # Queue was renamed to queue in Python 3
  import queue as Queue

import time
import logging
import argparse
import os
import re
import resource
import signal
import subprocess
import threading

from benchmark.benchmarkDataStructures import *
from benchmark.runexecutor import RunExecutor
import benchmark.runexecutor as runexecutor
import benchmark.util as Util
import benchmark.filewriter as filewriter
from benchmark.outputHandler import OutputHandler

MEMLIMIT = runexecutor.MEMLIMIT
TIMELIMIT = runexecutor.TIMELIMIT
CORELIMIT = runexecutor.CORELIMIT

DEFAULT_CLOUD_TIMELIMIT = 3600 # s
DEFAULT_CLOUD_MEMLIMIT = None

DEFAULT_CLOUD_MEMORY_REQUIREMENT = 15000 # MB
DEFAULT_CLOUD_CPUCORE_REQUIREMENT = 1 # one core
DEFAULT_CLOUD_CPUMODEL_REQUIREMENT = "" # empty string matches every model

# next lines are needed for stopping the script
WORKER_THREADS = []
STOPPED_BY_INTERRUPT = False

"""
Naming conventions:

TOOL: a verifier program that should be executed
EXECUTABLE: the executable file that should be called for running a TOOL
SOURCEFILE: one file that contains code that should be verified
RUN: one execution of a TOOL on one SOURCEFILE
RUNSET: a set of RUNs of one TOOL with at most one RUN per SOURCEFILE
RUNDEFINITION: a template for the creation of a RUNSET with RUNS from one or more SOURCEFILESETs
BENCHMARK: a list of RUNDEFINITIONs and SOURCEFILESETs for one TOOL
OPTION: a user-specified option to add to the command-line of the TOOL when it its run
CONFIG: the configuration of this script consisting of the command-line arguments given by the user

"run" always denotes a job to do and is never used as a verb.
"execute" is only used as a verb (this is what is done with a run).
A benchmark or a run set can also be executed, which means to execute all contained runs.

Variables ending with "file" contain filenames.
Variables ending with "tag" contain references to XML tag objects created by the XML parser.
"""


class Worker(threading.Thread):
    """
    A Worker is a deamonic thread, that takes jobs from the workingQueue and runs them.
    """
    workingQueue = Queue.Queue()

    def __init__(self, number, outputHandler):
        threading.Thread.__init__(self) # constuctor of superclass
        self.numberOfThread = number
        self.outputHandler = outputHandler
        self.runExecutor = RunExecutor()
        self.setDaemon(True)
        self.start()

    def run(self):
        while not Worker.workingQueue.empty() and not STOPPED_BY_INTERRUPT:
            currentRun = Worker.workingQueue.get_nowait()
            try:
                self.execute(currentRun)
            except BaseException as e:
                print(e)
            Worker.workingQueue.task_done()


    def execute(self, run):
        """
        This function executes the tool with a sourcefile with options.
        It also calls functions for output before and after the run.
        """
        self.outputHandler.outputBeforeRun(run)

        (run.wallTime, run.cpuTime, run.memUsage, returnvalue, output) = \
            self.runExecutor.executeRun(
                run.getCmdline(), run.benchmark.rlimits, run.logFile,
                myCpuIndex=self.numberOfThread,
                environments=run.benchmark.getEnvironments(),
                runningDir=run.benchmark.workingDirectory())

        if self.runExecutor.PROCESS_KILLED:
            # If the run was interrupted, we ignore the result and cleanup.
            run.wallTime = 0
            run.cpuTime = 0
            try:
                if config.debug:
                   os.rename(run.logFile, run.logFile + ".killed")
                else:
                   os.remove(run.logFile)
            except OSError:
                pass
            return

        run.afterExecution(returnvalue, output)
        self.outputHandler.outputAfterRun(run)


    def stop(self):
        # asynchronous call to runexecutor, 
        # the worker will stop asap, but not within this method.
        self.runExecutor.kill()


def executeBenchmarkLocaly(benchmark, outputHandler):
    
    runSetsExecuted = 0

    logging.debug("I will use {0} threads.".format(benchmark.numOfThreads))

    # iterate over run sets
    for runSet in benchmark.runSets:

        if STOPPED_BY_INTERRUPT: break

        (mod, rest) = config.moduloAndRest

        if not runSet.shouldBeExecuted() \
                or (runSet.index % mod != rest):
            outputHandler.outputForSkippingRunSet(runSet)

        elif not runSet.runs:
            outputHandler.outputForSkippingRunSet(runSet, "because it has no files")

        else:
            runSetsExecuted += 1
            # get times before runSet
            ruBefore = resource.getrusage(resource.RUSAGE_CHILDREN)
            wallTimeBefore = time.time()

            outputHandler.outputBeforeRunSet(runSet)

            # put all runs into a queue
            for run in runSet.runs:
                Worker.workingQueue.put(run)

            # create some workers
            for i in range(benchmark.numOfThreads):
                WORKER_THREADS.append(Worker(i, outputHandler))

            # wait until all tasks are done,
            # instead of queue.join(), we use a loop and sleep(1) to handle KeyboardInterrupt
            finished = False
            while not finished and not STOPPED_BY_INTERRUPT:
                try:
                    Worker.workingQueue.all_tasks_done.acquire()
                    finished = (Worker.workingQueue.unfinished_tasks == 0)
                finally:
                    Worker.workingQueue.all_tasks_done.release()

                try:
                    time.sleep(0.1) # sleep some time
                except KeyboardInterrupt:
                    killScriptLocal()

            # get times after runSet
            wallTimeAfter = time.time()
            usedWallTime = wallTimeAfter - wallTimeBefore
            ruAfter = resource.getrusage(resource.RUSAGE_CHILDREN)
            usedCpuTime = (ruAfter.ru_utime + ruAfter.ru_stime) \
                        - (ruBefore.ru_utime + ruBefore.ru_stime)

            outputHandler.outputAfterRunSet(runSet, usedCpuTime, usedWallTime)

    outputHandler.outputAfterBenchmark(STOPPED_BY_INTERRUPT)

    if config.commit and not STOPPED_BY_INTERRUPT and runSetsExecuted > 0:
        Util.addFilesToGitRepository(OUTPUT_PATH, outputHandler.allCreatedFiles,
                                     config.commitMessage+'\n\n'+outputHandler.description)


def parseCloudResultFile(filePath):

    wallTime = None
    cpuTime = None
    memUsage = None
    returnValue = None

    with open(filePath, 'rt') as file:

        try:
            wallTime = float(file.readline().split(":")[-1])
        except ValueError:
            pass
        try:
            cpuTime = float(file.readline().split(":")[-1])
        except ValueError:
            pass
        try:
            memUsage = int(file.readline().split(":")[-1]);
        except ValueError:
            pass
        try:
            returnValue = int(file.readline().split(":")[-1])
        except ValueError:
            pass

    return (wallTime, cpuTime, memUsage, returnValue)


def parseAndSetCloudWorkerHostInformation(filePath, outputHandler):

    runToHostMap = {}
    try:
        with open(filePath, 'rt') as file:
            outputHandler.allCreatedFiles.append(filePath)

            # Parse first part of information about hosts until first blank line
            while True:
                line = file.readline().strip()
                if not line:
                    break
                name = line.split("=")[-1].strip()
                osName = file.readline().split("=")[-1].strip()
                memory = file.readline().split("=")[-1].strip()
                cpuName = file.readline().split("=")[-1].strip()
                frequency = file.readline().split("=")[-1].strip()
                cores = file.readline().split("=")[-1].strip()
                outputHandler.storeSystemInfo(osName, cpuName, cores, frequency, memory, name)

            # Parse second part of information about runs
            for line in file:
                line = line.strip()
                if not line:
                    continue # skip empty lines

                runInfo = line.split('\t')
                runToHostMap[runInfo[1].strip()] = runInfo[0].strip()
                # TODO one key + multiple values <==> one sourcefile + multiple configs

    except IOError:
        logging.warning("Host information file not found: " + filePath)
    return runToHostMap


def toTabList(l):
    return "\t".join(map(str, l))


def commonBaseDir(l):
    # os.path.commonprefix returns the common prefix, not the common directory
    return os.path.dirname(os.path.commonprefix(l))


def getCloudInput(benchmark):

    (requirements, numberOfRuns, limitsAndNumRuns, runDefinitions, sourceFiles) = getBenchmarkDataForCloud(benchmark)
    (workingDir, toolpaths) = getToolDataForCloud(benchmark)
    
    # prepare cloud input, we make all paths absolute, TODO necessary?
    outputDir = benchmark.logFolder
    absOutputDir = os.path.abspath(outputDir)
    absWorkingDir = os.path.abspath(workingDir)
    absCloudRunExecutorDir = os.path.abspath(os.path.dirname(__file__))
    absToolpaths = list(map(os.path.abspath, toolpaths))
    absScriptsPath = os.path.abspath('scripts') # necessary files for non-CPAchecker-tools
    absSourceFiles = list(map(os.path.abspath, sourceFiles))
    absBaseDir = commonBaseDir(absSourceFiles + absToolpaths + [absScriptsPath] + [absCloudRunExecutorDir])

    if absBaseDir == "": sys.exit("No common base dir found.")

    numOfRunDefLinesAndPriorityStr = [numberOfRuns + 1] # add 1 for the headerline 
    if config.cloudPriority:
        numOfRunDefLinesAndPriorityStr.append(config.cloudPriority)

    # build the input for the cloud, 
    # see external vcloud/README.txt for details.
    cloudInput = [
                toTabList(absToolpaths + [absScriptsPath]),
                absCloudRunExecutorDir,
                toTabList([absBaseDir, absOutputDir, absWorkingDir]),
                toTabList(requirements)
            ]
    if benchmark.resultFilesPattern:
        cloudInput.append(benchmark.resultFilesPattern)

    cloudInput.extend([
                toTabList(numOfRunDefLinesAndPriorityStr),
                toTabList(limitsAndNumRuns)
            ])
    cloudInput.extend(runDefinitions)
    return "\n".join(cloudInput)


def getToolDataForCloud(benchmark):

    workingDir = benchmark.workingDirectory()
    if not os.path.isdir(workingDir):
        sys.exit("Missing working directory {0}, cannot run tool.", format(workingDir))
    logging.debug("Working dir: " + workingDir)

    toolpaths = benchmark.requiredFiles()
    for file in toolpaths:
        if not os.path.exists(file):
            sys.exit("Missing file {0}, cannot run benchmark within cloud.".format(os.path.normpath(file)))

    return (workingDir, toolpaths)


def getBenchmarkDataForCloud(benchmark):

    # get requirements
    r = benchmark.requirements
    requirements = [DEFAULT_CLOUD_MEMORY_REQUIREMENT if r.memory is None else r.memory,
                    DEFAULT_CLOUD_CPUCORE_REQUIREMENT if r.cpuCores is None else r.cpuCores,
                    DEFAULT_CLOUD_CPUMODEL_REQUIREMENT if r.cpuModel is None else r.cpuModel]

    # get limits and number of Runs
    timeLimit = benchmark.rlimits.get(TIMELIMIT, DEFAULT_CLOUD_TIMELIMIT)
    memLimit  = benchmark.rlimits.get(MEMLIMIT,  DEFAULT_CLOUD_MEMLIMIT)
    coreLimit = benchmark.rlimits.get(CORELIMIT, None)
    numberOfRuns = sum(len(runSet.runs) for runSet in benchmark.runSets if runSet.shouldBeExecuted())
    limitsAndNumRuns = [numberOfRuns, timeLimit, memLimit]
    if coreLimit is not None: limitsAndNumRuns.append(coreLimit)
    
    # get tool-specific environment
    env = benchmark.getEnvironments()

    # get Runs with args and sourcefiles
    sourceFiles = []
    runDefinitions = []
    for runSet in benchmark.runSets:
        if not runSet.shouldBeExecuted(): continue
        if STOPPED_BY_INTERRUPT: break

        # get runs
        for run in runSet.runs:

            # we assume, that VCloud-client only splits its input at tabs,
            # so we can use all other chars for the info, that is needed to run the tool.
            # we build a string-representation of all this info (it's a map),
            # that can be parsed with python again in cloudRunexecutor.py (this is very easy with eval()) .
            argString = repr({"args":run.getCmdline(), "env":env, "debug": config.debug})
            assert not "\t" in argString # cannot call toTabList(), if there is a tab

            logFile = os.path.relpath(run.logFile, benchmark.logFolder)
            runDefinitions.append(toTabList([argString, run.sourcefile, logFile]))
            sourceFiles.append(run.sourcefile)

    if not sourceFiles: sys.exit("Benchmark has nothing to run.")
        
    return (requirements, numberOfRuns, limitsAndNumRuns, runDefinitions, sourceFiles)


def handleCloudResults(benchmark, outputHandler):
    
    outputDir = benchmark.logFolder
    if not os.path.isdir(outputDir) or not os.listdir(outputDir):
        # outputDir does not exist or is empty
        logging.warning("Cloud produced no results. Output-directory is missing or empty: {0}".format(outputDir))

    # Write worker host informations in xml
    filePath = os.path.join(outputDir, "hostInformation.txt")
    runToHostMap = parseAndSetCloudWorkerHostInformation(filePath, outputHandler)

    # write results in runs and handle output after all runs are done
    executedAllRuns = True
    runsProducedErrorOutput = False
    for runSet in benchmark.runSets:
        if not runSet.shouldBeExecuted():
            outputHandler.outputForSkippingRunSet(runSet)
            continue

        outputHandler.outputBeforeRunSet(runSet)

        for run in runSet.runs:
            stdoutFile = run.logFile + ".stdOut"
            if not os.path.exists(stdoutFile):
                logging.warning("No results exist for file {0}.".format(run.sourcefile))
                executedAllRuns = False
                continue

            try:
                (run.wallTime, run.cpuTime, run.memUsage, returnValue) = parseCloudResultFile(stdoutFile)

                if run.sourcefile in runToHostMap:
                    run.host = runToHostMap[run.sourcefile]

                if returnValue is not None:
                    # Do not delete stdOut file if there was some problem
                    os.remove(stdoutFile)
                else:
                    executedAllRuns = False

            except EnvironmentError as e:
                logging.warning("Cannot extract measured values from output for file {0}: {1}".format(run.sourcefile, e))
                executedAllRuns = False
                continue

            if os.path.exists(run.logFile + ".stdError"):
                runsProducedErrorOutput = True

            outputHandler.outputBeforeRun(run)
            output = ''
            try:
                with open(run.logFile, 'rt') as outputFile:
                    # first 6 lines are for logging, rest is output of subprocess, see RunExecutor.py for details
                    output = '\n'.join(map(Util.decodeToString, outputFile.readlines()[6:]))
            except IOError as e:
                logging.warning("Cannot read log file: " + e.strerror)

            run.afterExecution(returnValue, output)
            outputHandler.outputAfterRun(run)

        outputHandler.outputAfterRunSet(runSet, None, None)

    outputHandler.outputAfterBenchmark(STOPPED_BY_INTERRUPT)

    if not executedAllRuns:
        logging.warning("Not all runs were executed in the cloud!")
    if runsProducedErrorOutput:
        logging.warning("Some runs produced unexpected warnings on stderr, please check the {0} files!"
                        .format(os.path.join(outputDir, '*.stdError')))


def executeBenchmarkInCloud(benchmark, outputHandler):

    # build input for cloud
    cloudInput = getCloudInput(benchmark)
    cloudInputFile = os.path.join(benchmark.logFolder, 'cloudInput.txt')
    filewriter.writeFile(cloudInput, cloudInputFile)
    outputHandler.allCreatedFiles.append(cloudInputFile)

    # install cloud and dependencies
    ant = subprocess.Popen(["ant", "resolve-benchmark-dependencies"])
    ant.communicate()
    ant.wait()

    # start cloud and wait for exit
    logging.debug("Starting cloud.")
    if config.debug:
        logLevel =  "FINER"
    else:
        logLevel = "INFO"
    libDir = os.path.abspath("./lib/java-benchmark")
    cmdLine = ["java", "-jar", libDir + "/vcloud.jar", "benchmark", "--loglevel", logLevel]
    if config.cloudMaster:
        cmdLine.extend(["--master", config.cloudMaster])
    cloud = subprocess.Popen(cmdLine, stdin=subprocess.PIPE)
    try:
        (out, err) = cloud.communicate(cloudInput.encode('utf-8'))
    except KeyboardInterrupt:
        killScriptCloud()
    returnCode = cloud.wait()

    if returnCode and not STOPPED_BY_INTERRUPT:
        logging.warn("Cloud return code: {0}".format(returnCode))

    handleCloudResults(benchmark, outputHandler)

    if config.commit and not STOPPED_BY_INTERRUPT:
        Util.addFilesToGitRepository(OUTPUT_PATH, outputHandler.allCreatedFiles,
                                     config.commitMessage+'\n\n'+outputHandler.description)


def executeBenchmark(benchmarkFile):
    benchmark = Benchmark(benchmarkFile, config, OUTPUT_PATH)
    outputHandler = OutputHandler(benchmark)
    
    logging.debug("I'm benchmarking {0} consisting of {1} run sets.".format(
            repr(benchmarkFile), len(benchmark.runSets)))

    if config.cloud:
        executeBenchmarkInCloud(benchmark, outputHandler)
    else:
        executeBenchmarkLocaly(benchmark, outputHandler)


def main(argv=None):

    if argv is None:
        argv = sys.argv
    parser = argparse.ArgumentParser(description=
        """Run benchmarks with a verification tool.
        Documented example files for the benchmark definitions
        can be found as 'doc/examples/benchmark*.xml'.
        Use the table-generator.py script to create nice tables
        from the output of this script.""")

    parser.add_argument("files", nargs='+', metavar="FILE",
                      help="XML file with benchmark definition")
    parser.add_argument("-d", "--debug",
                      action="store_true",
                      help="Enable debug output")

    parser.add_argument("-r", "--rundefinition", dest="selectedRunDefinitions",
                      action="append",
                      help="Run only the specified RUN_DEFINITION from the benchmark definition file. "
                            + "This option can be specified several times.",
                      metavar="RUN_DEFINITION")

    parser.add_argument("-t", "--test", dest="selectedRunDefinitions",
                      action="append",
                      help="Same as -r/--rundefinition (deprecated)",
                      metavar="TEST")

    parser.add_argument("-s", "--sourcefiles", dest="selectedSourcefileSets",
                      action="append",
                      help="Run only the files from the sourcefiles tag with SOURCE as name. "
                            + "This option can be specified several times.",
                      metavar="SOURCES")

    parser.add_argument("-n", "--name",
                      dest="name", default=None,
                      help="Set name of benchmark execution to NAME",
                      metavar="NAME")

    parser.add_argument("-o", "--outputpath",
                      dest="output_path", type=str,
                      default="./test/results/",
                      help="Output prefix for the generated results. "
                            + "If the path is a folder files are put into it,"
                            + "otherwise it is used as a prefix for the resulting files.")

    parser.add_argument("-T", "--timelimit",
                      dest="timelimit", default=None,
                      help="Time limit in seconds for each run (-1 to disable)",
                      metavar="SECONDS")

    parser.add_argument("-M", "--memorylimit",
                      dest="memorylimit", default=None,
                      help="Memory limit in MB (-1 to disable)",
                      metavar="MB")

    parser.add_argument("-N", "--numOfThreads",
                      dest="numOfThreads", default=None, type=int,
                      help="Run n benchmarks in parallel",
                      metavar="n")

    parser.add_argument("-x", "--moduloAndRest",
                      dest="moduloAndRest", default=(1,0), nargs=2, type=int,
                      help="Run only a subset of run definitions for which (i %% a == b) holds" +
                            "with i being the index of the run definition in the benchmark definition file " +
                            "(starting with 1).",
                      metavar=("a","b"))

    parser.add_argument("-c", "--limitCores", dest="corelimit",
                      type=int, default=None,
                      metavar="N",
                      help="Limit each run of the tool to N CPU cores (-1 to disable).")

    parser.add_argument("--commit", dest="commit",
                      action="store_true",
                      help="If the output path is a git repository without local changes,"
                            + "add and commit the result files.")

    parser.add_argument("--message",
                      dest="commitMessage", type=str,
                      default="Results for benchmark run",
                      help="Commit message if --commit is used.")

    parser.add_argument("--cloud",
                      dest="cloud",
                      action="store_true",
                      help="Use cloud to execute benchmarks.")

    parser.add_argument("--cloudMaster",
                      dest="cloudMaster",
                      metavar="HOST",
                      help="Sets the master host of the cloud to be used.")

    parser.add_argument("--cloudPriority",
                      dest="cloudPriority",
                      metavar="PRIORITY",
                      help="Sets the priority for this benchmark used in the cloud. Possible values are IDLE, LOW, HIGH, URGENT.")

    parser.add_argument("--cloudCPUModel",
                      dest="cloudCPUModel", type=str, default=None,
                      metavar="CPU_MODEL",
                      help="Only execute runs on CPU models that contain the given string.")
    
    # for backward-compatibility only
    parser.add_argument("--cloudCpuModel",
                      dest="cloudCPUModel", type=str, default=None,
                      metavar="CPU_MODEL",
                      help="Only execute runs on CPU models that contain the given string.")

    global config, OUTPUT_PATH
    config = parser.parse_args(argv[1:])
    if os.path.isdir(config.output_path):
        OUTPUT_PATH = os.path.normpath(config.output_path) + os.sep
    else:
        OUTPUT_PATH = config.output_path


    if config.debug:
        logging.basicConfig(format="%(asctime)s - %(levelname)s - %(message)s",
                            level=logging.DEBUG)
    else:
        logging.basicConfig(format="%(asctime)s - %(levelname)s - %(message)s")

    for arg in config.files:
        if not os.path.exists(arg) or not os.path.isfile(arg):
            parser.error("File {0} does not exist.".format(repr(arg)))

    if not config.cloud:
        try:
            processes = subprocess.Popen(['ps', '-eo', 'cmd'], stdout=subprocess.PIPE).communicate()[0]
            if len(re.findall("python.*benchmark\.py", Util.decodeToString(processes))) > 1:
                logging.warn("Already running instance of this script detected. " + \
                             "Please make sure to not interfere with somebody else's benchmarks.")
        except OSError:
            pass # this does not work on Windows

    for arg in config.files:
        if STOPPED_BY_INTERRUPT: break
        logging.debug("Benchmark {0} is started.".format(repr(arg)))
        executeBenchmark(arg)
        logging.debug("Benchmark {0} is done.".format(repr(arg)))

    logging.debug("I think my job is done. Have a nice day!")


def killScriptLocal():
        # set global flag
        global STOPPED_BY_INTERRUPT
        STOPPED_BY_INTERRUPT = True

        # kill running jobs
        Util.printOut("killing subprocesses...")
        for worker in WORKER_THREADS:
            worker.stop()

        # wait until all threads are stopped
        for worker in WORKER_THREADS:
            worker.join()


def killScriptCloud():
        # set global flag
        global STOPPED_BY_INTERRUPT
        STOPPED_BY_INTERRUPT = True

        # kill cloud-client, should be done automatically, when the subprocess is aborted


def signal_handler_ignore(signum, frame):
    logging.warn('Received signal %d, ignoring it' % signum)

if __name__ == "__main__":
    # ignore SIGTERM
    signal.signal(signal.SIGTERM, signal_handler_ignore)
    try:
        sys.exit(main())
    except KeyboardInterrupt: # this block is reached, when interrupt is thrown before or after a run set execution
        if config.cloud:
            killScriptCloud()
        else:
            killScriptLocal()
        Util.printOut("\n\nScript was interrupted by user, some runs may not be done.")
