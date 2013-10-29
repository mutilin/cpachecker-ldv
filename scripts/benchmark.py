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

<<<<<<< HEAD
# colors for column status in terminal
USE_COLORS = True
COLOR_GREEN = "\033[32;1m{0}\033[m"
COLOR_RED = "\033[31;1m{0}\033[m"
COLOR_ORANGE = "\033[33;1m{0}\033[m"
COLOR_MAGENTA = "\033[35;1m{0}\033[m"
COLOR_DEFAULT = "{0}"
COLOR_DIC = {result.RESULT_CORRECT_SAFE:   COLOR_GREEN,
             result.RESULT_CORRECT_UNSAFE: COLOR_GREEN,
             result.RESULT_UNKNOWN:        COLOR_ORANGE,
             result.RESULT_ERROR:          COLOR_MAGENTA,
             result.RESULT_WRONG_UNSAFE:   COLOR_RED,
             result.RESULT_WRONG_SAFE:     COLOR_RED,
             result.CATEGORY_UNKNOWN:      COLOR_DEFAULT,
             None: COLOR_DEFAULT}

TERMINAL_TITLE=''
_term = os.environ.get('TERM', '')
if _term.startswith(('xterm', 'rxvt')):
    TERMINAL_TITLE = "\033]0;Benchmark {0}\007"
elif _term.startswith('screen'):
    TERMINAL_TITLE = "\033kBenchmark {0}\033\\"

# the number of digits after the decimal separator of the time column,
# for the other columns it can be configured in the xml-file
TIME_PRECISION = 2

=======
DEFAULT_CLOUD_MEMORY_REQUIREMENT = 15000 # MB
DEFAULT_CLOUD_CPUCORE_REQUIREMENT = 1 # one core
DEFAULT_CLOUD_CPUMODEL_REQUIREMENT = "" # empty string matches every model
>>>>>>> master

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


<<<<<<< HEAD
class Benchmark:
    """
    The class Benchmark manages the import of source files, options, columns and
    the tool from a benchmarkFile.
    This class represents the <benchmark> tag.
    """

    def __init__(self, benchmarkFile):
        """
        The constructor of Benchmark reads the source files, options, columns and the tool
        from the XML in the benchmarkFile..
        """
        logging.debug("I'm loading the benchmark {0}.".format(benchmarkFile))

        self.benchmarkFile = benchmarkFile

        # get benchmark-name
        self.name = os.path.basename(benchmarkFile)[:-4] # remove ending ".xml"
        if config.name:
            self.name += "."+config.name

        # get current date as String to avoid problems, if script runs over midnight
        currentTime = time.localtime()
        self.date = time.strftime("%y-%m-%d_%H%M", currentTime)
        self.dateISO = time.strftime("%y-%m-%d %H:%M", currentTime)

        # parse XML
        rootTag = ET.ElementTree().parse(benchmarkFile)

        # get tool
        toolName = rootTag.get('tool')
        if not toolName:
            sys.exit('A tool needs to be specified in the benchmark definition file.')
        toolModule = "benchmark.tools." + toolName
        try:
            self.tool = __import__(toolModule, fromlist=['Tool']).Tool()
        except ImportError:
            sys.exit('Unsupported tool "{0}" specified.'.format(toolName))
        except AttributeError:
            sys.exit('The module for "{0}" does not define the necessary class.'.format(toolName))

        self.toolName = self.tool.getName()
        self.executable = self.tool.getExecutable()
        self.toolVersion = self.tool.getVersion(self.executable)

        logging.debug("The tool to be benchmarked is {0}.".format(repr(self.tool)))

        self.rlimits = {}
        keys = list(rootTag.keys())
        for limit in [MEMLIMIT, TIMELIMIT, CORELIMIT]:
            if limit in keys:
                self.rlimits[limit] = int(rootTag.get(limit))

        # override limits from XML with values from command line
        def overrideLimit(configVal, limit):
            if configVal != None:
                val = int(configVal)
                if val == -1: # infinity
                    if limit in self.rlimits:
                        self.rlimits.pop(limit)
                else:
                    self.rlimits[limit] = val

        overrideLimit(config.memorylimit, MEMLIMIT)
        overrideLimit(config.timelimit, TIMELIMIT)
        overrideLimit(config.corelimit, CORELIMIT)

        # get number of threads, default value is 1
        self.numOfThreads = int(rootTag.get("threads")) if ("threads" in keys) else 1
        if config.numOfThreads != None:
            self.numOfThreads = config.numOfThreads
        if self.numOfThreads < 1:
            logging.error("At least ONE thread must be given!")
            sys.exit()

        # create folder for file-specific log-files.
        # existing files (with the same name) will be OVERWRITTEN!
        self.outputBase = OUTPUT_PATH + self.name + "." + self.date
        self.logFolder = self.outputBase + ".logfiles/"
        if not os.path.isdir(self.logFolder):
            os.makedirs(self.logFolder)

        # get global options
        self.options = getOptionsFromXML(rootTag)

        # get columns
        self.columns = Benchmark.loadColumns(rootTag.find("columns"))

        # get global source files, they are used in all run sets
        globalSourcefilesTags = rootTag.findall("sourcefiles")

        # get required files
        self._requiredFiles = []
        baseDir = os.path.dirname(self.benchmarkFile)
        for requiredFilesTag in rootTag.findall('requiredfiles'):
            requiredFiles = Util.expandFileNamePattern(requiredFilesTag.text, baseDir)
            self._requiredFiles.extend(requiredFiles)

        # get requirements
        self.requirements = Requirements()
        for requireTag in rootTag.findall("require"):
            requirements = Requirements(requireTag.get('cpuModel', None),
                                        requireTag.get('cpuCores', None),
                                        requireTag.get('memory',   None)
                                        )
            self.requirements = Requirements.merge(self.requirements, config.cloudCpuModel)

        self.requirements = Requirements.mergeWithCpuModel(self.requirements, config.cloudCpuModel)
        self.requirements = Requirements.mergeWithLimits(self.requirements, self.rlimits)

        # get benchmarks
        self.runSets = []
        for (i, rundefinitionTag) in enumerate(rootTag.findall("rundefinition")):
            self.runSets.append(RunSet(rundefinitionTag, self, i+1, globalSourcefilesTags))

        if not self.runSets:
            for (i, rundefinitionTag) in enumerate(rootTag.findall("test")):
                self.runSets.append(RunSet(rundefinitionTag, self, i+1, globalSourcefilesTags))
            if self.runSets:
                logging.warning("Benchmark file {0} uses deprecated <test> tags. Please rename them to <rundefinition>.".format(benchmarkFile))
            else:
                logging.warning("Benchmark file {0} specifies no runs to execute (no <rundefinition> tags found).".format(benchmarkFile))

        self.outputHandler = OutputHandler(self)

    def requiredFiles(self):
        return self._requiredFiles + self.tool.getProgrammFiles(self.executable)

    @staticmethod
    def loadColumns(columnsTag):
        """
        @param columnsTag: the columnsTag from the XML file
        @return: a list of Columns()
        """

        logging.debug("I'm loading some columns for the outputfile.")
        columns = []
        if columnsTag != None: # columnsTag is optional in XML file
            for columnTag in columnsTag.findall("column"):
                pattern = columnTag.text
                title = columnTag.get("title", pattern)
                numberOfDigits = columnTag.get("numberOfDigits") # digits behind comma
                column = Column(pattern, title, numberOfDigits)
                columns.append(column)
                logging.debug('Column "{0}" with title "{1}" loaded from XML file.'
                          .format(column.text, column.title))
        return columns


class RunSet:
    """
    The class RunSet manages the import of files and options of a run set.
    """

    def __init__(self, rundefinitionTag, benchmark, index, globalSourcefilesTags=[]):
        """
        The constructor of RunSet reads run-set name and the source files from rundefinitionTag.
        Source files can be included or excluded, and imported from a list of
        names in another file. Wildcards and variables are expanded.
        @param rundefinitionTag: a rundefinitionTag from the XML file
        """

        self.benchmark = benchmark

        # get name of run set, name is optional, the result can be "None"
        self.realName = rundefinitionTag.get("name")

        # index is the number of the run set
        self.index = index

        self.logFolder = benchmark.logFolder
        if self.realName:
            self.logFolder += self.realName + "."

        # get all run-set-specific options from rundefinitionTag
        self.options = benchmark.options + getOptionsFromXML(rundefinitionTag)

        # get all runs, a run contains one sourcefile with options
        self.blocks = self.extractRunsFromXML(globalSourcefilesTags + rundefinitionTag.findall("sourcefiles"))
        self.runs = [run for block in self.blocks for run in block.runs]

        names = [self.realName]
        if len(self.blocks) == 1:
            # there is exactly one source-file set to run, append its name to run-set name
            names.append(self.blocks[0].realName)
        self.name = '.'.join(filter(None, names))
        self.fullName = self.benchmark.name + (("." + self.name) if self.name else "")


    def shouldBeExecuted(self):
        return not config.selectedRunDefinitions \
            or self.realName in config.selectedRunDefinitions


    def extractRunsFromXML(self, sourcefilesTagList):
        '''
        This function builds a list of SourcefileSets (containing filename with options).
        The files and their options are taken from the list of sourcefilesTags.
        '''
        # runs are structured as sourcefile sets, one set represents one sourcefiles tag
        blocks = []
        baseDir = os.path.dirname(self.benchmark.benchmarkFile)

        for index, sourcefilesTag in enumerate(sourcefilesTagList):
            sourcefileSetName = sourcefilesTag.get("name")
            matchName = sourcefileSetName or str(index)
            if (config.selectedSourcefileSets and matchName not in config.selectedSourcefileSets):
                continue

            # get list of filenames
            sourcefiles = self.getSourcefilesFromXML(sourcefilesTag, baseDir)

            # get file-specific options for filenames
            fileOptions = getOptionsFromXML(sourcefilesTag)

            currentRuns = []
            for sourcefile in sourcefiles:
                currentRuns.append(Run(sourcefile, fileOptions, self))

            blocks.append(SourcefileSet(sourcefileSetName, index, currentRuns))
        return blocks


    def getSourcefilesFromXML(self, sourcefilesTag, baseDir):
        sourcefiles = []

        # get included sourcefiles
        for includedFiles in sourcefilesTag.findall("include"):
            sourcefiles += self.expandFileNamePattern(includedFiles.text, baseDir)

        # get sourcefiles from list in file
        for includesFilesFile in sourcefilesTag.findall("includesfile"):

            for file in self.expandFileNamePattern(includesFilesFile.text, baseDir):

                # check for code (if somebody confuses 'include' and 'includesfile')
                if Util.isCode(file):
                    logging.error("'" + file + "' seems to contain code instead of a set of source file names.\n" + \
                        "Please check your benchmark definition file or remove bracket '{' from this file.")
                    sys.exit()

                # read files from list
                fileWithList = open(file, 'rt')
                for line in fileWithList:

                    # strip() removes 'newline' behind the line
                    line = line.strip()

                    # ignore comments and empty lines
                    if not Util.isComment(line):
                        sourcefiles += self.expandFileNamePattern(line, os.path.dirname(file))

                fileWithList.close()

        # remove excluded sourcefiles
        for excludedFiles in sourcefilesTag.findall("exclude"):
            excludedFilesList = self.expandFileNamePattern(excludedFiles.text, baseDir)
            for excludedFile in excludedFilesList:
                sourcefiles = Util.removeAll(sourcefiles, excludedFile)

        return sourcefiles


    def expandFileNamePattern(self, pattern, baseDir):
        """
        The function expandFileNamePattern expands a filename pattern to a sorted list
        of filenames. The pattern can contain variables and wildcards.
        If baseDir is given and pattern is not absolute, baseDir and pattern are joined.
        """

        # store pattern for fallback
        shortFileFallback = pattern

        # replace vars like ${benchmark_path},
        # with converting to list and back, we can use the function 'substituteVars()'
        expandedPattern = substituteVars([pattern], self)
        assert len(expandedPattern) == 1
        expandedPattern = expandedPattern[0]

        if expandedPattern != pattern:
            logging.debug("Expanded variables in expression {0} to {1}."
                .format(repr(pattern), repr(expandedPattern)))

        fileList = Util.expandFileNamePattern(expandedPattern, baseDir)

        # sort alphabetical,
        fileList.sort()

        if not fileList:
                logging.warning("No files found matching {0}."
                            .format(repr(pattern)))

        return fileList


class SourcefileSet():
    """
    A SourcefileSet contains a list of runs and a name.
    """
    def __init__(self, name, index, runs):
        self.realName = name # this name is optional
        self.name = name or str(index) # this name is always non-empty
        self.runs = runs


class Run():
=======
class Worker(threading.Thread):
>>>>>>> master
    """
    A Worker is a deamonic thread, that takes jobs from the workingQueue and runs them.
    """
    workingQueue = Queue.Queue()

<<<<<<< HEAD
    def __init__(self, sourcefile, fileOptions, runSet):
        self.sourcefile = sourcefile
        self.runSet = runSet
        self.benchmark = runSet.benchmark
        self.specificOptions = fileOptions # options that are specific for this run
        self.logFile = runSet.logFolder + os.path.basename(sourcefile) + ".log"
        self.options = substituteVars(runSet.options + fileOptions, # all options to be used when executing this run
                                      runSet,
                                      sourcefile)

        # Copy columns for having own objects in run
        # (we need this for storing the results in them).
        self.columns = [Column(c.text, c.title, c.numberOfDigits) for c in self.benchmark.columns]

        # dummy values, for output in case of interrupt
        self.status = ""
        self.cpuTime = 0
        self.wallTime = 0
        self.memUsage = None
        self.host = None

        self.tool = self.benchmark.tool
        args = self.tool.getCmdline(self.benchmark.executable, self.options, self.sourcefile)
        args = [os.path.expandvars(arg) for arg in args]
        args = [os.path.expanduser(arg) for arg in args]
        self.args = args;



    def afterExecution(self, returnvalue, output):

        rlimits = self.benchmark.rlimits

        # calculation: returnvalue == (returncode * 256) + returnsignal
        # highest bit of returnsignal shows only whether a core file was produced, we clear it
        returnsignal = returnvalue & 0x7F
        returncode = returnvalue >> 8
        logging.debug("My subprocess returned {0}, code {1}, signal {2}.".format(returnvalue, returncode, returnsignal))
        self.status = self.tool.getStatus(returncode, returnsignal, output, self._isTimeout())
        self.tool.addColumnValues(output, self.columns)

        # Tools sometimes produce a result even after a timeout.
        # This should not be counted, so we overwrite the result with TIMEOUT
        # here. if this is the case.
        # However, we don't want to forget more specific results like SEGFAULT,
        # so we do this only if the result is a "normal" one like SAFE.
        if not self.status in ['SAFE', 'UNSAFE', 'UNKNOWN']:
            if TIMELIMIT in rlimits:
                timeLimit = rlimits[TIMELIMIT] + 20
                if self.wallTime > timeLimit or self.cpuTime > timeLimit:
                    self.status = "TIMEOUT"
        if returnsignal == 9 \
                and MEMLIMIT in rlimits \
                and self.memUsage \
                and int(self.memUsage) >= (rlimits[MEMLIMIT] * 1024 * 1024):
            self.status = 'OUT OF MEMORY'

        self.benchmark.outputHandler.outputAfterRun(self)

    def execute(self, numberOfThread):
=======
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
>>>>>>> master
        """
        This function executes the tool with a sourcefile with options.
        It also calls functions for output before and after the run.
        """
        self.outputHandler.outputBeforeRun(run)

        (run.wallTime, run.cpuTime, run.memUsage, returnvalue, output) = \
            self.runExecutor.executeRun(
                run.args, run.benchmark.rlimits, run.logFile,
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

<<<<<<< HEAD
        self.afterExecution(returnvalue, output)

    def _isTimeout(self):
        ''' try to find out whether the tool terminated because of a timeout '''
        rlimits = self.benchmark.rlimits
        if TIMELIMIT in rlimits:
            limit = rlimits[TIMELIMIT]
        else:
            limit = float('inf')

        return self.cpuTime > limit*0.99


class Column:
    """
    The class Column contains text, title and numberOfDigits of a column.
    """

    def __init__(self, text, title, numOfDigits):
        self.text = text
        self.title = title
        self.numberOfDigits = numOfDigits
        self.value = ""


class Requirements:
    def __init__(self, cpuModel=None, cpuCores=None, memory=None):
        self._cpuModel = cpuModel
        self._cpuCores = int(cpuCores) if cpuCores is not None else None
        self._memory   = int(memory) if memory is not None else None

        if self.cpuCores() <= 0:
            raise Exception('Invalid value {} for required CPU cores.'.format(cpuCores))

        if self.memory() <= 0:
            raise Exception('Invalid value {} for required memory.'.format(memory))

    def cpuModel(self):
        return self._cpuModel or ""

    def cpuCores(self):
        return self._cpuCores or 1

    def memory(self):
        return self._memory or 15000

    @classmethod
    def merge(cls, r1, r2):
        if r1._cpuModel is not None and r2._cpuModel is not None:
            raise Exception('Double specification of required CPU model.')
        if r1._cpuCores and r2._cpuCores:
            raise Exception('Double specification of required CPU cores.')
        if r1._memory and r2._memory:
            raise Exception('Double specification of required memory.')

        return cls(r1._cpuModel if r1._cpuModel is not None else r2._cpuModel,
                   r1._cpuCores or r2._cpuCores,
                   r1._memory or r2._memory)

    @classmethod
    def mergeWithLimits(cls, r, l):
        _cpuModel = r._cpuModel
        _cpuCores = r._cpuCores
        _memory = r._memory

        if(_cpuCores is None and CORELIMIT in l):
            _cpuCores = l[CORELIMIT]
        if(_memory is None and MEMLIMIT in l):
            _memory = l[MEMLIMIT]

        return cls(_cpuModel, _cpuCores, _memory)

    @classmethod
    def mergeWithCpuModel(cls, r, cpuModel):
        _cpuCores = r._cpuCores
        _memory = r._memory

        if(cpuModel is None):
            return r
        else:
            return cls(cpuModel, _cpuCores, _memory)


    def __repr__(self):
        return "%s(%r)" % (self.__class__, self.__dict__)

    def __str__(self):
        s = ""
        if self._cpuModel:
            s += " CPU='" + self._cpuModel + "'"
        if self._cpuCores:
            s += " Cores=" + str(self._cpuCores)
        if self._memory:
            s += " Memory=" + str(self._memory) + "MB"

        return "Requirements:" + (s if s else " None")


class OutputHandler:
    """
    The class OutputHandler manages all outputs to the terminal and to files.
    """

    printLock = threading.Lock()

    def __init__(self, benchmark):
        """
        The constructor of OutputHandler collects information about the benchmark and the computer.
        """

        self.allCreatedFiles = []
        self.benchmark = benchmark
        self.statistics = Statistics()

        # get information about computer
        (opSystem, cpuModel, numberOfCores, maxFrequency, memory, hostname) = self.getSystemInfo()
        version = self.benchmark.toolVersion

        memlimit = None
        timelimit = None
        corelimit = None
        if MEMLIMIT in self.benchmark.rlimits:
            memlimit = str(self.benchmark.rlimits[MEMLIMIT]) + " MB"
        if TIMELIMIT in self.benchmark.rlimits:
            timelimit = str(self.benchmark.rlimits[TIMELIMIT]) + " s"
        if CORELIMIT in self.benchmark.rlimits:
            corelimit = str(self.benchmark.rlimits[CORELIMIT])

        self.storeHeaderInXML(version, memlimit, timelimit, corelimit, opSystem, cpuModel,
                              numberOfCores, maxFrequency, memory, hostname)
        self.writeHeaderToLog(version, memlimit, timelimit, corelimit, opSystem, cpuModel,
                              numberOfCores, maxFrequency, memory, hostname)

        self.XMLFileNames = []



    def storeSystemInfo(self, opSystem, cpuModel, numberOfCores, maxFrequency, memory, hostname):
        osElem = ET.Element("os", {"name":opSystem})
        cpuElem = ET.Element("cpu", {"model":cpuModel, "cores":numberOfCores, "frequency":maxFrequency})
        ramElem = ET.Element("ram", {"size":memory})
        systemInfo = ET.Element("systeminfo", {"hostname":hostname})
        systemInfo.append(osElem)
        systemInfo.append(cpuElem)
        systemInfo.append(ramElem)
        self.XMLHeader.append(systemInfo)

    def storeHeaderInXML(self, version, memlimit, timelimit, corelimit, opSystem,
                         cpuModel, numberOfCores, maxFrequency, memory, hostname):

        # store benchmarkInfo in XML
        self.XMLHeader = ET.Element("result",
                    {"benchmarkname": self.benchmark.name, "date": self.benchmark.dateISO,
                     "tool": self.benchmark.toolName, "version": version})
        if memlimit:
            self.XMLHeader.set(MEMLIMIT, memlimit)
        if timelimit:
            self.XMLHeader.set(TIMELIMIT, timelimit)
        if corelimit:
            self.XMLHeader.set(CORELIMIT, corelimit)

        if(not config.cloud):
            # store systemInfo in XML
            self.storeSystemInfo(opSystem, cpuModel, numberOfCores, maxFrequency, memory, hostname)

        # store columnTitles in XML
        columntitlesElem = ET.Element("columns")
        columntitlesElem.append(ET.Element("column", {"title": "status"}))
        columntitlesElem.append(ET.Element("column", {"title": "cputime"}))
        columntitlesElem.append(ET.Element("column", {"title": "walltime"}))
        for column in self.benchmark.columns:
            columnElem = ET.Element("column", {"title": column.title})
            columntitlesElem.append(columnElem)
        self.XMLHeader.append(columntitlesElem)

        # Build dummy entries for output, later replaced by the results,
        # The dummy XML elements are shared over all runs.
        self.XMLDummyElems = [ET.Element("column", {"title": "status", "value": ""}),
                      ET.Element("column", {"title": "cputime", "value": ""}),
                      ET.Element("column", {"title": "walltime", "value": ""})]
        for column in self.benchmark.columns:
            self.XMLDummyElems.append(ET.Element("column",
                        {"title": column.title, "value": ""}))


    def writeHeaderToLog(self, version, memlimit, timelimit, corelimit, opSystem,
                         cpuModel, numberOfCores, maxFrequency, memory, hostname):
        """
        This method writes information about benchmark and system into TXTFile.
        """

        columnWidth = 20
        simpleLine = "-" * (60) + "\n\n"

        header = "   BENCHMARK INFORMATION\n"\
                + "benchmark:".ljust(columnWidth) + self.benchmark.name + "\n"\
                + "date:".ljust(columnWidth) + self.benchmark.dateISO + "\n"\
                + "tool:".ljust(columnWidth) + self.benchmark.toolName\
                + " " + version + "\n"

        if memlimit:
            header += "memlimit:".ljust(columnWidth) + memlimit + "\n"
        if timelimit:
            header += "timelimit:".ljust(columnWidth) + timelimit + "\n"
        if corelimit:
            header += "CPU cores used:".ljust(columnWidth) + corelimit + "\n"
        header += simpleLine

        systemInfo = "   SYSTEM INFORMATION\n"\
                + "host:".ljust(columnWidth) + hostname + "\n"\
                + "os:".ljust(columnWidth) + opSystem + "\n"\
                + "cpu:".ljust(columnWidth) + cpuModel + "\n"\
                + "- cores:".ljust(columnWidth) + numberOfCores + "\n"\
                + "- max frequency:".ljust(columnWidth) + maxFrequency + "\n"\
                + "ram:".ljust(columnWidth) + memory + "\n"\
                + simpleLine

        self.description = header + systemInfo

        runSetName = None
        runSets = [runSet for runSet in self.benchmark.runSets if runSet.shouldBeExecuted()]
        if len(runSets) == 1:
            # in case there is only a single run set to to execute, we can use its name
            runSetName = runSets[0].name

        # write to file
        TXTFileName = self.getFileName(runSetName, "txt")
        self.TXTFile = filewriter.FileWriter(TXTFileName, self.description)
        self.allCreatedFiles.append(TXTFileName)

    def getSystemInfo(self):
        """
        This function returns some information about the computer.
        """

        # get info about OS
        (sysname, name, kernel, version, machine) = os.uname()
        opSystem = sysname + " " + kernel + " " + machine

        # get info about CPU
        cpuInfo = dict()
        maxFrequency = 'unknown'
        cpuInfoFilename = '/proc/cpuinfo'
        if os.path.isfile(cpuInfoFilename) and os.access(cpuInfoFilename, os.R_OK):
            cpuInfoFile = open(cpuInfoFilename, 'rt')
            cpuInfo = dict(tuple(str.split(':')) for str in
                            cpuInfoFile.read()
                            .replace('\n\n', '\n').replace('\t', '')
                            .strip('\n').split('\n'))
            cpuInfoFile.close()
        cpuModel = cpuInfo.get('model name', 'unknown').strip()
        numberOfCores = cpuInfo.get('cpu cores', 'unknown').strip()
        if 'cpu MHz' in cpuInfo:
            maxFrequency = cpuInfo['cpu MHz'].split('.')[0].strip() + ' MHz'

        # modern cpus may not work with full speed the whole day
        # read the number from cpufreq and overwrite maxFrequency from above
        freqInfoFilename = '/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq'
        if os.path.isfile(freqInfoFilename) and os.access(freqInfoFilename, os.R_OK):
            frequencyInfoFile = open(freqInfoFilename, 'rt')
            maxFrequency = frequencyInfoFile.read().strip('\n')
            frequencyInfoFile.close()
            maxFrequency = str(int(maxFrequency) // 1000) + ' MHz'

        # get info about memory
        memInfo = dict()
        memInfoFilename = '/proc/meminfo'
        if os.path.isfile(memInfoFilename) and os.access(memInfoFilename, os.R_OK):
            memInfoFile = open(memInfoFilename, 'rt')
            memInfo = dict(tuple(str.split(': ')) for str in
                            memInfoFile.read()
                            .replace('\t', '')
                            .strip('\n').split('\n'))
            memInfoFile.close()
        memTotal = memInfo.get('MemTotal', 'unknown').strip()

        return (opSystem, cpuModel, numberOfCores, maxFrequency, memTotal, name)


    def outputBeforeRunSet(self, runSet):
        """
        The method outputBeforeRunSet() calculates the length of the
        first column for the output in terminal and stores information
        about the runSet in XML.
        @param runSet: current run set
        """

        self.runSet = runSet
        numberOfFiles = len(runSet.runs)

        logging.debug("Run set {0} consists of {1} sourcefiles.".format(
                runSet.index, numberOfFiles))

        sourcefiles = [run.sourcefile for run in runSet.runs]

        # common prefix of file names
        self.commonPrefix = os.path.commonprefix(sourcefiles) # maybe with parts of filename
        self.commonPrefix = self.commonPrefix[: self.commonPrefix.rfind('/') + 1] # only foldername

        # length of the first column in terminal
        self.maxLengthOfFileName = max([len(file) for file in sourcefiles])
        self.maxLengthOfFileName = max(20, self.maxLengthOfFileName - len(self.commonPrefix))

        # write run set name to terminal
        numberOfFiles = ("     (1 file)" if numberOfFiles == 1
                        else "     ({0} files)".format(numberOfFiles))
        Util.printOut("\nexecuting run set"
            + (" '" + runSet.name + "'" if runSet.name else "")
            + numberOfFiles
            + (TERMINAL_TITLE.format(runSet.fullName) if USE_COLORS and sys.stdout.isatty() else ""))

        # write information about the run set into TXTFile
        self.writeRunSetInfoToLog(runSet)

        # prepare information for text output
        for run in runSet.runs:
            run.resultline = self.formatSourceFileName(run.sourcefile)

        # prepare XML structure for each run and runSet
            run.xml = ET.Element("sourcefile", {"name": run.sourcefile})
            if run.specificOptions:
                run.xml.set("options", " ".join(run.specificOptions))
            run.xml.extend(self.XMLDummyElems)

        runSet.xml = self.runsToXML(runSet, runSet.runs)

        # write (empty) results to TXTFile and XML
        self.TXTFile.append(self.runSetToTXT(runSet), False)
        XMLFileName = self.getFileName(runSet.name, "xml")
        self.XMLFile = filewriter.FileWriter(XMLFileName,
                       Util.XMLtoString(runSet.xml))
        self.XMLFile.lastModifiedTime = time.time()
        self.allCreatedFiles.append(XMLFileName)
        self.XMLFileNames.append(XMLFileName)


    def outputForSkippingRunSet(self, runSet, reason=None):
        '''
        This function writes a simple message to terminal and logfile,
        when a run set is skipped.
        There is no message about skipping a run set in the xml-file.
        '''

        # print to terminal
        Util.printOut("\nSkipping run set" +
               (" '" + runSet.name + "'" if runSet.name else "") +
               (" " + reason if reason else "")
              )

        # write into TXTFile
        runSetInfo = "\n\n"
        if runSet.name:
            runSetInfo += runSet.name + "\n"
        runSetInfo += "Run set {0} of {1}: skipped {2}\n".format(
                runSet.index, len(self.benchmark.runSets), reason or "")
        self.TXTFile.append(runSetInfo)


    def writeRunSetInfoToLog(self, runSet):
        """
        This method writes the information about a run set into the TXTFile.
        """

        runSetInfo = "\n\n"
        if runSet.name:
            runSetInfo += runSet.name + "\n"
        runSetInfo += "Run set {0} of {1} with options: {2}\n\n".format(
                runSet.index, len(self.benchmark.runSets),
                " ".join(runSet.options))

        titleLine = self.createOutputLine("sourcefile", "status", "cpu time",
                            "wall time", self.benchmark.columns, True)

        runSet.simpleLine = "-" * (len(titleLine))

        runSetInfo += titleLine + "\n" + runSet.simpleLine + "\n"

        # write into TXTFile
        self.TXTFile.append(runSetInfo)


    def outputBeforeRun(self, run):
        """
        The method outputBeforeRun() prints the name of a file to terminal.
        It returns the name of the logfile.
        @param run: a Run object
        """
        # output in terminal
        try:
            OutputHandler.printLock.acquire()

            timeStr = time.strftime("%H:%M:%S", time.localtime()) + "   "
            progressIndicator = " ({0}/{1})".format(self.runSet.runs.index(run), len(self.runSet.runs))
            terminalTitle = TERMINAL_TITLE.format(self.runSet.fullName + progressIndicator) if USE_COLORS and sys.stdout.isatty() else ""
            if self.benchmark.numOfThreads == 1:
                Util.printOut(terminalTitle
                              + timeStr + self.formatSourceFileName(run.sourcefile), '')
            else:
                Util.printOut(terminalTitle + timeStr + "starting   " + self.formatSourceFileName(run.sourcefile))
        finally:
            OutputHandler.printLock.release()

        # get name of file-specific log-file
        self.allCreatedFiles.append(run.logFile)


    def outputAfterRun(self, run):
        """
        The method outputAfterRun() prints filename, result, time and status
        of a run to terminal and stores all data in XML
        """

        # format times, type is changed from float to string!
        cpuTimeStr = Util.formatNumber(run.cpuTime, TIME_PRECISION)
        wallTimeStr = Util.formatNumber(run.wallTime, TIME_PRECISION)

        # format numbers, numberOfDigits is optional, so it can be None
        for column in run.columns:
            if column.numberOfDigits is not None:

                # if the number ends with "s" or another letter, remove it
                if (not column.value.isdigit()) and column.value[-2:-1].isdigit():
                    column.value = column.value[:-1]

                try:
                    floatValue = float(column.value)
                    column.value = Util.formatNumber(floatValue, column.numberOfDigits)
                except ValueError: # if value is no float, don't format it
                    pass

        # store information in run
        run.resultline = self.createOutputLine(run.sourcefile, run.status,
                cpuTimeStr, wallTimeStr, run.columns)
        self.addValuesToRunXML(run, cpuTimeStr, wallTimeStr)

        # output in terminal/console
        statusRelation = result.getResultCategory(run.sourcefile, run.status)
        if USE_COLORS and sys.stdout.isatty(): # is terminal, not file
            statusStr = COLOR_DIC[statusRelation].format(run.status.ljust(8))
        else:
            statusStr = run.status.ljust(8)

        try:
            OutputHandler.printLock.acquire()

            valueStr = statusStr + cpuTimeStr.rjust(8) + wallTimeStr.rjust(8)
            if self.benchmark.numOfThreads == 1:
                Util.printOut(valueStr)
            else:
                timeStr = time.strftime("%H:%M:%S", time.localtime()) + " "*14
                Util.printOut(timeStr + self.formatSourceFileName(run.sourcefile) + valueStr)

            # write result in TXTFile and XML
            self.TXTFile.append(self.runSetToTXT(run.runSet), False)
            self.statistics.addResult(statusRelation)

            # we don't want to write this file to often, it can slow down the whole script,
            # so we wait at least 10 seconds between two write-actions
            currentTime = time.time()
            if currentTime - self.XMLFile.lastModifiedTime > 10:
                self.XMLFile.replace(Util.XMLtoString(run.runSet.xml))
                self.XMLFile.lastModifiedTime = currentTime

        finally:
            OutputHandler.printLock.release()


    def outputAfterRunSet(self, runSet, cpuTime, wallTime):
        """
        The method outputAfterRunSet() stores the times of a run set in XML.
        @params cpuTime, wallTime: accumulated times of the run set
        """

        # write results to files
        self.XMLFile.replace(Util.XMLtoString(runSet.xml))

        if len(runSet.blocks) > 1:
            for block in runSet.blocks:
                blockFileName = self.getFileName(runSet.name, block.name + ".xml")
                filewriter.writeFile(blockFileName,
                    Util.XMLtoString(self.runsToXML(runSet, block.runs, block.name)))
                self.allCreatedFiles.append(blockFileName)

        self.TXTFile.append(self.runSetToTXT(runSet, True, cpuTime, wallTime))


    def runSetToTXT(self, runSet, finished=False, cpuTime=0, wallTime=0):
        lines = []

        # store values of each run
        for run in runSet.runs: lines.append(run.resultline)

        lines.append(runSet.simpleLine)
=======
        run.afterExecution(returnvalue, output)
        self.outputHandler.outputAfterRun(run)
>>>>>>> master


    def stop(self):
        # asynchronous call to runexecutor, 
        # the worker will stop asap, but not within this method.
        self.runExecutor.kill()


<<<<<<< HEAD
        return "\n".join(lines) + "\n"

    def runsToXML(self, runSet, runs, blockname=None):
        """
        This function creates the XML structure for a list of runs
        """
        # copy benchmarkinfo, limits, columntitles, systeminfo from XMLHeader
        runsElem = Util.getCopyOfXMLElem(self.XMLHeader)
        runsElem.set("options", " ".join(runSet.options))
        if blockname is not None:
            runsElem.set("block", blockname)
            runsElem.set("name", ((runSet.realName + ".") if runSet.realName else "") + blockname)
        elif runSet.realName:
            runsElem.set("name", runSet.realName)

        # collect XMLelements from all runs
        for run in runs: runsElem.append(run.xml)

        return runsElem


    def addValuesToRunXML(self, run, cpuTimeStr, wallTimeStr):
        """
        This function adds the result values to the XML representation of a run.
        """
        runElem = run.xml
        for elem in list(runElem):
            runElem.remove(elem)
        runElem.append(ET.Element("column", {"title": "status", "value": run.status}))
        runElem.append(ET.Element("column", {"title": "cputime", "value": cpuTimeStr}))
        runElem.append(ET.Element("column", {"title": "walltime", "value": wallTimeStr}))
        if run.memUsage is not None:
            runElem.append(ET.Element("column", {"title": "memUsage", "value": str(run.memUsage)}))
        if run.host:
            runElem.append(ET.Element("column", {"title": "host", "value": run.host}))

        for column in run.columns:
            runElem.append(ET.Element("column",
                        {"title": column.title, "value": column.value}))


    def createOutputLine(self, sourcefile, status, cpuTimeDelta, wallTimeDelta, columns, isFirstLine=False):
        """
        @param sourcefile: title of a sourcefile
        @param status: status of programm
        @param cpuTimeDelta: time from running the programm
        @param wallTimeDelta: time from running the programm
        @param columns: list of columns with a title or a value
        @param isFirstLine: boolean for different output of headline and other lines
        @return: a line for the outputFile
        """

        lengthOfStatus = 8
        lengthOfTime = 11
        minLengthOfColumns = 8

        outputLine = self.formatSourceFileName(sourcefile) + \
                     status.ljust(lengthOfStatus) + \
                     cpuTimeDelta.rjust(lengthOfTime) + \
                     wallTimeDelta.rjust(lengthOfTime)

        for column in columns:
            columnLength = max(minLengthOfColumns, len(column.title)) + 2

            if isFirstLine:
                value = column.title
            else:
                value = column.value

            outputLine = outputLine + str(value).rjust(columnLength)

        return outputLine


    def outputAfterBenchmark(self):
        self.statistics.printToTerminal()

        if self.XMLFileNames:
            Util.printOut("In order to get HTML and CSV tables, run\n{0} '{1}'"
                          .format(os.path.join(os.path.dirname(__file__), 'table-generator.py'),
                                  "' '".join(self.XMLFileNames)))

        if STOPPED_BY_INTERRUPT:
            Util.printOut("\nScript was interrupted by user, some runs may not be done.\n")


    def getFileName(self, runSetName, fileExtension):
        '''
        This function returns the name of the file for a run set
        with an extension ("txt", "xml").
        '''

        fileName = self.benchmark.outputBase + ".results."

        if runSetName:
            fileName += runSetName + "."

        return fileName + fileExtension


    def formatSourceFileName(self, fileName):
        '''
        Formats the file name of a program for printing on console.
        '''
        fileName = fileName.replace(self.commonPrefix, '', 1)
        return fileName.ljust(self.maxLengthOfFileName + 4)


class Statistics:

    def __init__(self):
        self.dic = dict((status,0) for status in COLOR_DIC)
        self.counter = 0

    def addResult(self, statusRelation):
        self.counter += 1
        assert statusRelation in self.dic
        self.dic[statusRelation] += 1


    def printToTerminal(self):
        Util.printOut('\n'.join(['\nStatistics:' + str(self.counter).rjust(13) + ' Files',
                 '    correct:        ' + str(self.dic[result.RESULT_CORRECT_SAFE] + \
                                              self.dic[result.RESULT_CORRECT_UNSAFE]).rjust(4),
                 '    unknown:        ' + str(self.dic[result.RESULT_UNKNOWN] + \
                                              self.dic[result.RESULT_ERROR]).rjust(4),
                 '    false positives:' + str(self.dic[result.RESULT_WRONG_UNSAFE]).rjust(4) + \
                 '        (file is safe, result is unsafe)',
                 '    false negatives:' + str(self.dic[result.RESULT_WRONG_SAFE]).rjust(4) + \
                 '        (file is unsafe, result is safe)',
                 '']))


def getOptionsFromXML(optionsTag):
    '''
    This function searches for options in a tag
    and returns a list with command-line arguments.
    '''
    return Util.toSimpleList([(option.get("name"), option.text)
               for option in optionsTag.findall("option")])


def substituteVars(oldList, runSet, sourcefile=None):
    """
    This method replaces special substrings from a list of string
    and return a new list.
    """

    benchmark = runSet.benchmark

    # list with tuples (key, value): 'key' is replaced by 'value'
    keyValueList = [('${benchmark_name}', benchmark.name),
                    ('${benchmark_date}', benchmark.date),
                    ('${benchmark_path}', os.path.dirname(benchmark.benchmarkFile)),
                    ('${benchmark_path_abs}', os.path.abspath(os.path.dirname(benchmark.benchmarkFile))),
                    ('${benchmark_file}', os.path.basename(benchmark.benchmarkFile)),
                    ('${benchmark_file_abs}', os.path.abspath(os.path.basename(benchmark.benchmarkFile))),
                    ('${logfile_path}',   os.path.dirname(runSet.logFolder)),
                    ('${logfile_path_abs}', os.path.abspath(runSet.logFolder)),
                    ('${rundefinition_name}', runSet.realName if runSet.realName else ''),
                    ('${test_name}',      runSet.realName if runSet.realName else '')]

    if sourcefile:
        keyValueList.append(('${sourcefile_name}', os.path.basename(sourcefile)))
        keyValueList.append(('${sourcefile_path}', os.path.dirname(sourcefile)))
        keyValueList.append(('${sourcefile_path_abs}', os.path.dirname(os.path.abspath(sourcefile))))

    # do not use keys twice
    assert len(set((key for (key, value) in keyValueList))) == len(keyValueList)

    newList = []

    for oldStr in oldList:
        newStr = oldStr
        for (key, value) in keyValueList:
            newStr = newStr.replace(key, value)
        if '${' in newStr:
            logging.warn("a variable was not replaced in '{0}'".format(newStr))
        newList.append(newStr)

    return newList



class Worker(threading.Thread):
    """
    A Worker is a deamonic thread, that takes jobs from the workingQueue and runs them.
    """
    workingQueue = Queue.Queue()

    def __init__(self, number):
        threading.Thread.__init__(self) # constuctor of superclass
        self.number = number
        self.setDaemon(True)
        self.start()

    def run(self):
        while not Worker.workingQueue.empty() and not STOPPED_BY_INTERRUPT:
            currentRun = Worker.workingQueue.get_nowait()
            try:
                currentRun.execute(self.number)
            except BaseException as e:
                print(e)
            Worker.workingQueue.task_done()

def executeBenchmarkLocaly(benchmark):
    outputHandler = benchmark.outputHandler
=======
def executeBenchmarkLocaly(benchmark, outputHandler):
    
>>>>>>> master
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
<<<<<<< HEAD

            name = file.readline().split("=")[-1].strip()
            osName = file.readline().split("=")[-1].strip()
            memory = file.readline().split("=")[-1].strip()
            cpuName = file.readline().split("=")[-1].strip()
            frequency = file.readline().split("=")[-1].strip()
            cores = file.readline().split("=")[-1].strip()
            outputHandler.storeSystemInfo(osName, cpuName, cores, frequency, memory, name)

            # skip all further hostdescriptions for now and wait for separator line
            while file.readline() != '\n':
                pass
=======
>>>>>>> master

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

<<<<<<< HEAD
def executeBenchmarkInCloud(benchmark):

    outputHandler = benchmark.outputHandler
=======

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
>>>>>>> master

    toolpaths = benchmark.requiredFiles()
    for file in toolpaths:
        if not os.path.exists(file):
            sys.exit("Missing file {0}, cannot run benchmark within cloud.".format(os.path.normpath(file)))

<<<<<<< HEAD
    requirements = str(benchmark.requirements.memory()) + "\t" + \
                str(benchmark.requirements.cpuCores())

    if(benchmark.requirements.cpuModel() is not ""):
        requirements += "\t" + benchmark.requirements.cpuModel()

    cloudRunExecutorDir = os.path.abspath(os.path.dirname(__file__))
    outputDir = benchmark.logFolder
    absOutputDir = os.path.abspath(outputDir)

    runDefinitions = []
    absSourceFiles = []
    numOfRunDefLines = 0

    # iterate over run sets
    for runSet in benchmark.runSets:
        if not runSet.shouldBeExecuted():
            continue
=======
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
>>>>>>> master

    # get Runs with args and sourcefiles
    sourceFiles = []
    runDefinitions = []
    for runSet in benchmark.runSets:
        if not runSet.shouldBeExecuted(): continue
        if STOPPED_BY_INTERRUPT: break
<<<<<<< HEAD

        numOfRunDefLines += (len(runSet.runs) + 1)

        timeLimit = str(DEFAULT_CLOUD_TIMELIMIT)
        memLimit = str(DEFAULT_CLOUD_MEMLIMIT)
        if(TIMELIMIT in benchmark.rlimits):
            timeLimit = str(benchmark.rlimits[TIMELIMIT])
        if(MEMLIMIT in benchmark.rlimits):
            memLimit = str(benchmark.rlimits[MEMLIMIT])

        runSetHeadLine = str(len(runSet.runs)) + "\t" + \
                        timeLimit + "\t" + \
                       memLimit

        if(CORELIMIT in benchmark.rlimits):
           coreLimit = str(benchmark.rlimits[CORELIMIT])
           runSetHeadLine += ("\t" + coreLimit)

        runDefinitions.append(runSetHeadLine)

        # iterate over runs
        for run in runSet.runs:
            #escape delimiter char
            args = []
            for arg in run.args:
                args.append(arg.replace(" ", "  "))
            argString = " ".join(args)

            logFile = os.path.relpath(run.logFile, outputDir)
            runDefinitions.append(argString + "\t" + run.sourcefile + "\t" + \
                                    logFile)
            absSourceFiles.append(os.path.abspath(run.sourcefile))
=======
>>>>>>> master

        # get runs
        for run in runSet.runs:

<<<<<<< HEAD
    #preparing cloud input
    absToolpaths = list(map(os.path.abspath, toolpaths))
    sourceFilesBaseDir = os.path.commonprefix(absSourceFiles)
    toolPathsBaseDir = os.path.commonprefix(absToolpaths)
    baseDir = os.path.commonprefix([sourceFilesBaseDir, toolPathsBaseDir, cloudRunExecutorDir])

    if(baseDir == ""):
        sys.exit("No common base dir found.")

    #os.path.commonprefix works on charakters not on the file system
    if(baseDir[-1]!='/'):
        baseDir = os.path.split(baseDir)[0];

    numOfRunDefLinesAndPriorityStr = str(numOfRunDefLines)
    if(config.cloudPriority):
        numOfRunDefLinesAndPriorityStr += "\t" + config.cloudPriority

    cloudInput = "\t".join(absToolpaths) + "\n" + \
                cloudRunExecutorDir + "\n" + \
                baseDir + "\t" + absOutputDir + "\t" + absWorkingDir +"\n" + \
                requirements + "\n" + \
                numOfRunDefLinesAndPriorityStr + "\n" + \
                "\n".join(runDefinitions)
=======
            # we assume, that VCloud-client only splits its input at tabs,
            # so we can use all other chars for the info, that is needed to run the tool.
            # we build a string-representation of all this info (it's a map),
            # that can be parsed with python again in cloudRunexecutor.py (this is very easy with eval()) .
            argString = repr({"args":run.args, "env":env, "debug": config.debug})
            assert not "\t" in argString # cannot call toTabList(), if there is a tab
>>>>>>> master

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

<<<<<<< HEAD
    executedAllRuns = True;

    #write results in runs and
    #handle output after all runs are done
=======
    # write results in runs and handle output after all runs are done
    executedAllRuns = True
>>>>>>> master
    for runSet in benchmark.runSets:
        if not runSet.shouldBeExecuted():
            outputHandler.outputForSkippingRunSet(runSet)
            continue

        outputHandler.outputBeforeRunSet(runSet)
<<<<<<< HEAD
=======

>>>>>>> master
        for run in runSet.runs:
            try:
                stdoutFile = run.logFile + ".stdOut"
                (run.wallTime, run.cpuTime, run.memUsage, returnValue) = parseCloudResultFile(stdoutFile)

<<<<<<< HEAD
                if(run.sourcefile in runToHostMap):
=======
                if run.sourcefile in runToHostMap:
>>>>>>> master
                    run.host = runToHostMap[run.sourcefile]

                if returnValue is not None:
                    # Do not delete stdOut file if there was some problem
                    os.remove(stdoutFile)
                    pass
                else:
                    executedAllRuns = False;

            except EnvironmentError as e:
                logging.warning("Cannot extract measured values from output for file {0}: {1}".format(run.sourcefile, e))
                executedAllRuns = False;
                continue

            outputHandler.outputBeforeRun(run)
            output = ''
            try:
                with open(run.logFile, 'rt') as f:
                    output = f.read()
            except IOError as e:
                logging.warning("Cannot read log file: " + e.strerror)

            run.afterExecution(returnValue, output)
            outputHandler.outputAfterRun(run)

        outputHandler.outputAfterRunSet(runSet, None, None)

<<<<<<< HEAD
    outputHandler.outputAfterBenchmark()
=======
    outputHandler.outputAfterBenchmark(STOPPED_BY_INTERRUPT)
>>>>>>> master

    if not executedAllRuns:
         logging.warning("Not all runs were executed in the cloud!")


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

<<<<<<< HEAD
    if(config.cloud):
        executeBenchmarkInCloud(benchmark)
=======
    if config.cloud:
        executeBenchmarkInCloud(benchmark, outputHandler)
>>>>>>> master
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
<<<<<<< HEAD
                      help="Use cloud with given host as master.")
=======
                      help="Sets the master host of the cloud to be used.")
>>>>>>> master

    parser.add_argument("--cloudPriority",
                      dest="cloudPriority",
                      metavar="PRIORITY",
                      help="Sets the priority for this benchmark used in the cloud. Possible values are IDLE, LOW, HIGH, URGENT.")

    parser.add_argument("--cloudCpuModel",
                      dest="cloudCpuModel", type=str, default=None,
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

    # Temporarily disabled because of problems with hanging ps processes.
    #if not config.cloud:
    #    try:
    #        processes = subprocess.Popen(['ps', '-eo', 'cmd'], stdout=subprocess.PIPE).communicate()[0]
    #        if len(re.findall("python.*benchmark\.py", Util.decodeToString(processes))) > 1:
    #            logging.warn("Already running instance of this script detected. " + \
    #                         "Please make sure to not interfere with somebody else's benchmarks.")
    #    except OSError:
    #        pass # this does not work on Windows

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
