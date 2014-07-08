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

import logging
import os
import time
import xml.etree.ElementTree as ET
import sys

from datetime import date

from . import result
from . import util as Util

MEMLIMIT = "memlimit"
TIMELIMIT = "timelimit"
CORELIMIT = "cpuCores"

SOFTTIMELIMIT = 'softtimelimit'
HARDTIMELIMIT = 'hardtimelimit'

PROPERTY_TAG = "propertyfile"

_BYTE_FACTOR = 1000 # byte in kilobyte

def substituteVars(oldList, runSet, sourcefile=None):
    """
    This method replaces special substrings from a list of string
    and return a new list.
    """
    benchmark = runSet.benchmark

    # list with tuples (key, value): 'key' is replaced by 'value'
    keyValueList = [('${benchmark_name}',     benchmark.name),
                    ('${benchmark_date}',     benchmark.date),
                    ('${benchmark_instance}', benchmark.instance),
                    ('${benchmark_path}',     benchmark.baseDir or '.'),
                    ('${benchmark_path_abs}', os.path.abspath(benchmark.baseDir)),
                    ('${benchmark_file}',     os.path.basename(benchmark.benchmarkFile)),
                    ('${benchmark_file_abs}', os.path.abspath(os.path.basename(benchmark.benchmarkFile))),
                    ('${logfile_path}',       os.path.dirname(runSet.logFolder) or '.'),
                    ('${logfile_path_abs}',   os.path.abspath(runSet.logFolder)),
                    ('${rundefinition_name}', runSet.realName if runSet.realName else ''),
                    ('${test_name}',          runSet.realName if runSet.realName else '')]

    if sourcefile:
        keyValueList.append(('${sourcefile_name}', os.path.basename(sourcefile)))
        keyValueList.append(('${sourcefile_path}', os.path.dirname(sourcefile) or '.'))
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



class Benchmark:
    """
    The class Benchmark manages the import of source files, options, columns and
    the tool from a benchmarkFile.
    This class represents the <benchmark> tag.
    """

    def __init__(self, benchmarkFile, config, OUTPUT_PATH):
        """
        The constructor of Benchmark reads the source files, options, columns and the tool
        from the XML in the benchmarkFile..
        """
        logging.debug("I'm loading the benchmark {0}.".format(benchmarkFile))

        self.config = config
        self.benchmarkFile = benchmarkFile
        self.baseDir = os.path.dirname(self.benchmarkFile)

        # get benchmark-name
        self.name = os.path.basename(benchmarkFile)[:-4] # remove ending ".xml"
        if config.name:
            self.name += "."+config.name

        # get current date as String to avoid problems, if script runs over midnight
        currentTime = time.localtime()
        self.date = time.strftime("%y-%m-%d_%H%M", currentTime)
        self.dateISO = time.strftime("%y-%m-%d %H:%M", currentTime)
        self.instance = self.date
        if not config.benchmarkInstanceIdent is None:
            self.instance = config.benchmarkInstanceIdent

        self.outputBase = OUTPUT_PATH + self.name + "." + self.instance
        self.logFolder = self.outputBase + ".logfiles" + os.path.sep
        if not config.reprocessResults and os.path.exists(self.logFolder):
            # we refuse to overwrite existing results
            sys.exit('Output directory {0} already exists, will not overwrite existing results.'.format(self.logFolder))

        # parse XML
        rootTag = ET.ElementTree().parse(benchmarkFile)

        # get tool
        toolName = rootTag.get('tool')
        if not toolName:
            sys.exit('A tool needs to be specified in the benchmark definition file.')
        toolModule = "benchmark.tools." + toolName
        try:
            self.tool = __import__(toolModule, fromlist=['Tool']).Tool()
        except ImportError as ie:
            sys.exit('Unsupported tool "{0}" specified. ImportError: {1}'.format(toolName, ie))
        except AttributeError:
            sys.exit('The module for "{0}" does not define the necessary class.'.format(toolName))

        self.toolName = self.tool.getName()
        self.toolVersion = ''
        self.executable = ''
        if not config.appengine:
            self.executable = self.tool.getExecutable()
            self.toolVersion = self.tool.getVersion(self.executable)

        logging.debug("The tool to be benchmarked is {0}.".format(str(self.toolName)))

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

        if HARDTIMELIMIT in keys:
            hardtimelimit = int(rootTag.get(HARDTIMELIMIT))
            if TIMELIMIT in self.rlimits:
                if hardtimelimit < self.rlimits[TIMELIMIT]:
                    logging.warning('Hard timelimit %d is smaller than timelimit %d, ignoring the former.'
                                    % (hardtimelimit, self.rlimits[TIMELIMIT]))
                else:
                    self.rlimits[SOFTTIMELIMIT] = self.rlimits[TIMELIMIT]
                    self.rlimits[TIMELIMIT] = hardtimelimit
            else:
                self.rlimits[TIMELIMIT] = hardtimelimit

        # get number of threads, default value is 1
        self.numOfThreads = int(rootTag.get("threads")) if ("threads" in keys) else 1
        if config.numOfThreads != None:
            self.numOfThreads = config.numOfThreads
        if self.numOfThreads < 1:
            logging.error("At least ONE thread must be given!")
            sys.exit()

        # create folder for file-specific log-files.
        os.makedirs(self.logFolder)

        # get global options and propertyFiles
        self.options = Util.getListFromXML(rootTag)
        self.propertyFiles = Util.getListFromXML(rootTag, tag=PROPERTY_TAG, attributes=[])

        # get columns
        self.columns = Benchmark.loadColumns(rootTag.find("columns"))

        # get global source files, they are used in all run sets
        globalSourcefilesTags = rootTag.findall("sourcefiles")

        # get required files
        self._requiredFiles = set()
        for requiredFilesTag in rootTag.findall('requiredfiles'):
            requiredFiles = Util.expandFileNamePattern(requiredFilesTag.text, self.baseDir)
            if not requiredFiles:
                logging.warning('Pattern {0} in requiredfiles tag did not match any file.'.format(requiredFilesTag.text))
            self._requiredFiles = self._requiredFiles.union(requiredFiles)

        # get requirements
        self.requirements = Requirements(rootTag.findall("require"), self.rlimits, config.cloudCPUModel)

        self.resultFilesPattern = None
        resultFilesTags = rootTag.findall("resultfiles")
        if resultFilesTags:
            if len(resultFilesTags) > 1:
                logger.warning("Benchmark file {0} has multiple <resultfiles> tags, ignoring all but the first.")
            self.resultFilesPattern = resultFilesTags[0].text

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

        if not any(runSet.shouldBeExecuted() for runSet in self.runSets):
            logging.warning("No runSet selected, nothing will be executed.")
            if config.selectedRunDefinitions:
                logging.warning("The selection {0} does not match any runSet of {1}".format(
                    str(config.selectedRunDefinitions),
                    str([runSet.realName for runSet in self.runSets])
                    ))


    def requiredFiles(self):
        return self._requiredFiles.union(self.tool.getProgrammFiles(self.executable))


    def addRequiredFile(self, filename=None):
        if filename is not None:
            self._requiredFiles.add(filename)


    def workingDirectory(self):
        return self.tool.getWorkingDirectory(self.executable)


    def getEnvironments(self):
        return self.tool.getEnvironments(self.executable)


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
        self.options = benchmark.options + Util.getListFromXML(rundefinitionTag)
        self.propertyFiles = benchmark.propertyFiles + Util.getListFromXML(rundefinitionTag, tag=PROPERTY_TAG, attributes=[])

        # get run-set specific required files
        requiredFiles = set()
        for requiredFilesTag in rundefinitionTag.findall('requiredfiles'):
            thisRequiredFiles = Util.expandFileNamePattern(requiredFilesTag.text, benchmark.baseDir)
            if not thisRequiredFiles:
                logging.warning('Pattern {0} in requiredfiles tag did not match any file.'.format(requiredFilesTag.text))
            requiredFiles = requiredFiles.union(thisRequiredFiles)

        # get all runs, a run contains one sourcefile with options
        self.blocks = self.extractRunsFromXML(globalSourcefilesTags + rundefinitionTag.findall("sourcefiles"),
                                              requiredFiles)
        self.runs = [run for block in self.blocks for run in block.runs]

        names = [self.realName]
        if len(self.blocks) == 1:
            # there is exactly one source-file set to run, append its name to run-set name
            names.append(self.blocks[0].realName)
        self.name = '.'.join(filter(None, names))
        self.fullName = self.benchmark.name + (("." + self.name) if self.name else "")

        # Currently we store logfiles as "basename.log",
        # so we cannot distinguish sourcefiles in different folder with same basename.
        # For a 'local benchmark' this causes overriding of logfiles after reading them,
        # so the result is correct, only the logfile is gone.
        # For 'cloud-mode' the logfile is overridden before reading it,
        # so the result will be wrong and every measured value will be missing.
        if self.shouldBeExecuted():
            sourcefilesSet = set()
            for run in self.runs:
                base = os.path.basename(run.identifier)
                if base in sourcefilesSet:
                    logging.warning("sourcefile with basename '" + base + 
                    "' appears twice in runset. This could cause problems with equal logfile-names.")
                else:
                    sourcefilesSet.add(base)
            del sourcefilesSet


    def shouldBeExecuted(self):
        return not self.benchmark.config.selectedRunDefinitions \
            or self.realName in self.benchmark.config.selectedRunDefinitions


    def extractRunsFromXML(self, sourcefilesTagList, globalRequiredFiles):
        '''
        This function builds a list of SourcefileSets (containing filename with options).
        The files and their options are taken from the list of sourcefilesTags.
        '''
        # runs are structured as sourcefile sets, one set represents one sourcefiles tag
        blocks = []

        for index, sourcefilesTag in enumerate(sourcefilesTagList):
            sourcefileSetName = sourcefilesTag.get("name")
            matchName = sourcefileSetName or str(index)
            if self.benchmark.config.selectedSourcefileSets \
                and matchName not in self.benchmark.config.selectedSourcefileSets:
                    continue

            requiredFiles = set()
            for requiredFilesTag in sourcefilesTag.findall('requiredfiles'):
                thisRequiredFiles = Util.expandFileNamePattern(requiredFilesTag.text, self.benchmark.baseDir)
                if not thisRequiredFiles:
                    logging.warning('Pattern {0} in requiredfiles tag did not match any file.'.format(requiredFilesTag.text))
                requiredFiles = requiredFiles.union(thisRequiredFiles)

            # get lists of filenames
            sourcefiles = self.getSourcefilesFromXML(sourcefilesTag, self.benchmark.baseDir)

            # get file-specific options for filenames
            fileOptions = Util.getListFromXML(sourcefilesTag)
            propertyFiles = Util.getListFromXML(sourcefilesTag, tag=PROPERTY_TAG, attributes=[])

            currentRuns = []
            for sourcefile in sourcefiles:
                currentRuns.append(Run(sourcefile, fileOptions, self, propertyFiles,
                                       list(globalRequiredFiles.union(requiredFiles))))

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

        for excludesFilesFile in sourcefilesTag.findall("excludesfile"):
            for file in self.expandFileNamePattern(excludesFilesFile.text, baseDir):
                # read files from list
                fileWithList = open(file, 'rt')
                for line in fileWithList:

                    # strip() removes 'newline' behind the line
                    line = line.strip()

                    # ignore comments and empty lines
                    if not Util.isComment(line):
                        excludedFilesList = self.expandFileNamePattern(line, os.path.dirname(file))
                        for excludedFile in excludedFilesList:
                            sourcefiles = Util.removeAll(sourcefiles, excludedFile)

                fileWithList.close()

        # add runs for cases without source files
        for run in sourcefilesTag.findall("withoutfile"):
            sourcefiles.append(run.text)

        # some runs need more than one sourcefile, 
        # the first sourcefile is a normal 'include'-file, we use its name as identifier for logfile and result-category
        # all other files are 'append'ed.
        sourcefilesLists = []
        appendFileTags = sourcefilesTag.findall("append")
        for sourcefile in sourcefiles:
            files = [sourcefile]
            for appendFile in appendFileTags:
                files.extend(self.expandFileNamePattern(appendFile.text, baseDir, sourcefile=sourcefile))
            sourcefilesLists.append(files)
        
        return sourcefilesLists


    def expandFileNamePattern(self, pattern, baseDir, sourcefile=None):
        """
        The function expandFileNamePattern expands a filename pattern to a sorted list
        of filenames. The pattern can contain variables and wildcards.
        If baseDir is given and pattern is not absolute, baseDir and pattern are joined.
        """

        # store pattern for fallback
        shortFileFallback = pattern

        # replace vars like ${benchmark_path},
        # with converting to list and back, we can use the function 'substituteVars()'
        expandedPattern = substituteVars([pattern], self, sourcefile)
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


loggedMissingPropertyFiles = set()


class Run():
    """
    A Run contains some sourcefile, some options, propertyfiles and some other stuff, that is needed for the Run.
    """

    def __init__(self, sourcefiles, fileOptions, runSet, propertyFiles=[], requiredFiles=[]):
        assert sourcefiles
        self.identifier = sourcefiles[0] # used for name of logfile, substitution, result-category
        self.sourcefiles = Util.getFiles(sourcefiles) # expand directories to get their sub-files
        logging.debug("Creating Run with identifier '{0}' and files {1}".format(self.identifier, self.sourcefiles))
        self.runSet = runSet
        self.specificOptions = fileOptions # options that are specific for this run
        self.logFile = runSet.logFolder + os.path.basename(self.identifier) + ".log"
        self.requiredFiles = requiredFiles

        # lets reduce memory-consumption: if 2 lists are equal, do not use the second one
        self.options = runSet.options + fileOptions if fileOptions else runSet.options # all options to be used when executing this run
        substitutedOptions = substituteVars(self.options, runSet, self.identifier)
        if substitutedOptions != self.options: self.options = substitutedOptions # for less memory again

        # get propertyfile for Run: if available, use the last one
        if propertyFiles:
            self.propertyfile = propertyFiles[-1]
        elif runSet.propertyFiles:
            self.propertyfile = runSet.propertyFiles[-1]
        else:
            self.propertyfile = None

        # replace run-specific stuff in the propertyfile and add it to the set of required files
        if self.propertyfile is None:
            if not self.propertyfile in loggedMissingPropertyFiles:
                loggedMissingPropertyFiles.add(self.propertyfile)
                logging.warning('No propertyfile specified. Results will be handled as UNKNOWN.')
        else:
            # we check two cases: direct filename or user-defined substitution, one of them must be a 'file'
            # TODO: do we need the second case? it is equal to previous used option "-spec ${sourcefile_path}/ALL.prp"
            expandedPropertyFiles = Util.expandFileNamePattern(self.propertyfile, self.runSet.benchmark.baseDir)
            substitutedPropertyfiles = substituteVars([self.propertyfile], runSet, self.identifier)
            assert len(substitutedPropertyfiles) == 1
            
            if expandedPropertyFiles:
                self.propertyfile = expandedPropertyFiles[0] # take only the first one
            elif substitutedPropertyfiles and os.path.isfile(substitutedPropertyfiles[0]):
                self.propertyfile = substitutedPropertyfiles[0]
            else:
                if not self.propertyfile in loggedMissingPropertyFiles:
                    loggedMissingPropertyFiles.add(self.propertyfile)
                    logging.warning('Pattern {0} in propertyfile tag did not match any file. It will be ignored.'.format(self.propertyfile))
                self.propertyfile = None

            self.runSet.benchmark.addRequiredFile(self.propertyfile)

        # Copy columns for having own objects in run
        # (we need this for storing the results in them).
        self.columns = [Column(c.text, c.title, c.numberOfDigits) for c in self.runSet.benchmark.columns]

        # here we store the optional result values, e.g. memory usage, energy, host name
        # keys need to be strings, if first character is "@" the value is marked as hidden (e.g., debug info)
        self.values = {}

        # dummy values, for output in case of interrupt
        self.status = ""
        self.cpuTime = None
        self.wallTime = None
        self.category = result.CATEGORY_UNKNOWN


    def getCmdline(self):
        args = self.runSet.benchmark.tool.getCmdline(self.runSet.benchmark.executable, self.options, self.sourcefiles, self.propertyfile)
        args = [os.path.expandvars(arg) for arg in args]
        args = [os.path.expanduser(arg) for arg in args]
        return args;


    def afterExecution(self, returnvalue, output, forceTimeout=False):

        rlimits = self.runSet.benchmark.rlimits
        isTimeout = forceTimeout or self._isTimeout()

        if returnvalue is not None:
            # calculation: returnvalue == (returncode * 256) + returnsignal
            # highest bit of returnsignal shows only whether a core file was produced, we clear it
            returnsignal = returnvalue & 0x7F
            returncode = returnvalue >> 8
            logging.debug("My subprocess returned {0}, code {1}, signal {2}.".format(returnvalue, returncode, returnsignal))
            self.status = self.runSet.benchmark.tool.getStatus(returncode, returnsignal, output, isTimeout)
        self.category = result.getResultCategory(self.identifier, self.status, self.propertyfile)
        self.runSet.benchmark.tool.addColumnValues(output, self.columns)

        
        # Tools sometimes produce a result even after a timeout.
        # This should not be counted, so we overwrite the result with TIMEOUT
        # here. if this is the case.
        # However, we don't want to forget more specific results like SEGFAULT,
        # so we do this only if the result is a "normal" one like TRUE.
        if self.status in result.STATUS_LIST and isTimeout:
            self.status = "TIMEOUT"
            self.category = result.CATEGORY_ERROR
        if returnvalue is not None \
                and returnsignal == 9 \
                and MEMLIMIT in rlimits \
                and 'memUsage' in self.values \
                and not self.values['memUsage'] is None \
                and int(self.values['memUsage']) >= (rlimits[MEMLIMIT] * _BYTE_FACTOR * _BYTE_FACTOR * 0.999):
            self.status = 'OUT OF MEMORY'
            self.category = result.CATEGORY_ERROR


    def _isTimeout(self):
        ''' try to find out whether the tool terminated because of a timeout '''
        rlimits = self.runSet.benchmark.rlimits
        if SOFTTIMELIMIT in rlimits:
            limit = rlimits[SOFTTIMELIMIT]
        elif TIMELIMIT in rlimits:
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
    '''
    This class wrappes the values for the requirements.
    It parses the tags from XML to get those values.
    If no values are found, at least the limits are used as requirements.
    If the user gives a cpuModel, it overrides the previous cpuModel.
    '''
    def __init__(self, tags, rlimits, cloudCPUModel):
        
        self.cpuModel = None
        self.memory   = None
        self.cpuCores = None
        
        for requireTag in tags:
            
            cpuModel = requireTag.get('cpuModel', None)
            if self.cpuModel is None:
                self.cpuModel = cpuModel
            else:
                raise Exception('Double specification of required CPU model.')

            cpuCores = requireTag.get('cpuCores', None)
            if self.cpuCores is None:
                if cpuCores is not None: self.cpuCores = int(cpuCores)
            else:
                raise Exception('Double specification of required CPU cores.')

            memory = requireTag.get('memory',   None)
            if self.memory is None:
                if memory is not None: self.memory = int(memory)
            else:
                raise Exception('Double specification of required memory.')

        # TODO check, if we have enough requirements to reach the limits        
        # TODO is this really enough? we need some overhead!
        if self.cpuCores is None:
            self.cpuCores = rlimits.get(CORELIMIT, None)

        if self.memory is None:
            self.memory = rlimits.get(MEMLIMIT, None)

        if cloudCPUModel is not None: # user-given model -> override value
            self.cpuModel = cloudCPUModel

        if self.cpuCores is not None and self.cpuCores <= 0:
            raise Exception('Invalid value {} for required CPU cores.'.format(self.cpuCores))

        if self.memory is not None and self.memory <= 0:
            raise Exception('Invalid value {} for required memory.'.format(self.memory))


    def __str__(self):
        s = ""
        if self.cpuModel:
            s += " CPU='" + self.cpuModel + "'"
        if self.cpuCores:
            s += " Cores=" + str(self.cpuCores)
        if self.memory:
            s += " Memory=" + str(self.memory) + "MB"

        return "Requirements:" + (s if s else " None")


