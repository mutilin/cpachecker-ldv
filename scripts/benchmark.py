#!/usr/bin/env python

from datetime import date

import time
import glob
import logging
import os.path
import platform
import resource
import signal
import subprocess
import sys
import xml.etree.ElementTree as ET

OUTPUT_PATH = "./test/results/"

CSV_SEPARATOR = "\t"

BUG_SUBSTRING_LIST = ['bug', 'unsafe']


# colors for column status in terminal
USE_COLORS = True
COLOR_GREEN = "\033[32;1m{0}\033[m"
COLOR_RED = "\033[31;1m{0}\033[m"
COLOR_ORANGE = "\033[35;1m{0}\033[m" # not orange, magenta
COLOR_DIC = {"correctSafe": COLOR_GREEN,
             "correctUnsafe": COLOR_GREEN,
             "unknown": COLOR_ORANGE,
             "wrongUnsafe": COLOR_RED,
             "wrongSafe": COLOR_RED}


# the number of digits after the decimal separator of the time column,
# for the other columns it can be configured in the xml-file
TIME_PRECISION = 2

USE_ONLY_DATE = False # use date or date+time for filenames

class Benchmark:
    """
    The class Benchmark manages the import of files, options, columns and
    the tool from a benchmarkFile.
    """

    def __init__(self, benchmarkFile):
        """
        The constructor of Benchmark reads the files, options, columns and the tool
        from the xml-file.
        """

        ## looks like trouble with pyxml, better use lxml (http://codespeak.net/lxml/).
        # try:
        #     from xml.parsers.xmlproc  import xmlval
        #     validator = xmlval.XMLValidator()
        #     validator.parse_resource(benchmarkFile)
        # except ImportError:
        #     logging.debug("I cannot import xmlval so I'm skipping the validation.")
        #     logging.debug("If you want xml validation please install pyxml.")
        logging.debug("I'm loading the benchmark {0}.".format(benchmarkFile))
        root = ET.ElementTree().parse(benchmarkFile)

        # get benchmark-name
        self.name = os.path.basename(benchmarkFile)[:-4] # remove ending ".xml"

        # get current date as String to avoid problems, if script runs over midnight
        if USE_ONLY_DATE:
            self.date = str(date.today())
        else:
            self.date = time.strftime("%y-%m-%d.%H%M", time.localtime())

        # get tool
        self.tool = root.get("tool")
        logging.debug("The tool to be benchmarked is {0}.".format(repr(self.tool)))

        self.rlimits = {}
        keys = list(root.keys())
        if ("memlimit" in keys):
            limit = int(root.get("memlimit")) * 1024 * 1024
            self.rlimits[resource.RLIMIT_AS] = (limit, limit)
        if ("timelimit" in keys):
            limit = int(root.get("timelimit"))
            self.rlimits[resource.RLIMIT_CPU] = (limit, limit)

        # get global files, they are tested in all tests
        globalSourcefiles = getSourceFiles(root.findall("sourcefiles"))

        # get benchmarks
        self.tests = []
        for testTag in root.findall("test"):
            self.tests.append(Test(testTag, globalSourcefiles))

        # get columns
        self.columns = self.loadColumns(root.find("columns"))

        # get global options
        self.options = getOptions(root)


    def loadColumns(self, columnsTag):
        """
        @param columnsTag: the columnsTag from the xml-file
        @return: a list of Columns()
        """

        logging.debug("I'm loading some columns for the outputfile.")
        columns = []
        if columnsTag != None: # columnsTag is optional in xml-file
            for columnTag in columnsTag.findall("column"):
                column = Column(columnTag)
                columns.append(column)
                logging.debug('Column "{0}" with title "{1}" loaded from xml-file.'
                          .format(column.text, column.title))
        return columns


class Test:
    """
    The class Test manages the import of files and options of a test.
    """

    def __init__(self, testTag, globalSourcefiles=[]):
        """
        The constructor of Test reads testname and the filenames from testTag.
        Filenames can be included or excluded, and imported from a list of
        names in another file. Wildcards and variables are expanded.
        @param testTag: a testTag from the xml-file
        """

        # get name of test, name is optional, the result can be "None"
        self.name = testTag.get("name")

        # get all sourcefiles
        self.sourcefiles = globalSourcefiles + \
                           getSourceFiles(testTag.findall("sourcefiles"))
        # print str(self.sourcefiles).replace('),', '),\n')

        # get all test-specific options from testTag
        self.options = getOptions(testTag)


def getSourceFiles(sourcefilesTagList):
    '''
    The function getSourceFiles returns a list of tuples (filename, options).
    The files and their options are taken from the list of sourcefilesTags.
    '''
    sourcefiles = []

    for sourcefilesTag in sourcefilesTagList:
        currentSourcefiles = []

        # get included sourcefiles
        for includedFiles in sourcefilesTag.findall("include"):
            currentSourcefiles += getFileList(includedFiles.text)

        # get sourcefiles from list in file
        for includesFilesFile in sourcefilesTag.findall("includesfile"):

            for file in getFileList(includesFilesFile.text):
                fileDir = os.path.dirname(file)

                # check for code (if somebody changes 'include' and 'includesfile')
                if isCode(file):
                    logging.error("'" + file + "' is no includesfile (set-file).\n" + \
                        "please check your benchmark-xml-file or remove bracket '{' from this file.")
                    sys.exit()

                # read files from list
                fileWithList = open(file, "r")
                for line in fileWithList:

                    # strip() removes 'newline' behind the line
                    line = line.strip()

                    # ignore comments and empty lines
                    if not isComment(line):
                        currentSourcefiles += getFileList(line, fileDir)

                fileWithList.close()

        # remove excluded sourcefiles
        for excludedFiles in sourcefilesTag.findall("exclude"):
            excludedFilesList = getFileList(excludedFiles.text)
            for excludedFile in excludedFilesList:
                currentSourcefiles = removeAll(currentSourcefiles, excludedFile)

        # get file-specific options for filenames
        fileOptions = getOptions(sourcefilesTag)

        sourcefiles.extend((file, fileOptions) for file in currentSourcefiles)

    return sourcefiles


def isCode(filename):
    '''
    This function returns True, if  a line of the file contains bracket '{'.
    '''
    isCodeFile = False
    file = open(filename, "r")
    for line in file:
        # ignore comments and empty lines
        if not isComment(line) \
                and '{' in line: # <-- simple indicator for code
            isCodeFile = True
    file.close()
    return isCodeFile


def isComment(line):
    return not line or line.startswith("#") or line.startswith("//")


def removeAll(list, elemToRemove):
    return [elem for elem in list if elem != elemToRemove]


def getFileList(shortFile, root=""):
    """
    The function getFileList expands a short filename to a sorted list
    of filenames. The short filename can contain variables and wildcards.
    If root is given and shortFile is not absolute, root and shortFile are joined.
    """
    # store shortFile for fallback
    shortFileFallback = shortFile

    # 'join' ignores root, if shortFile is absolute.
    # 'normpath' replaces 'A/foo/../B' with 'A/B', for pretty printing only
    shortFile = os.path.normpath(os.path.join(root, shortFile))

    # expand tilde and variables
    expandedFile = os.path.expandvars(os.path.expanduser(shortFile))

    # expand wildcards
    fileList = glob.glob(expandedFile)

    # sort alphabetical,
    # if list is emtpy, sorting returns None, so better do not sort
    if len(fileList) != 0:
        fileList.sort()

    if expandedFile != shortFile:
        logging.debug("Expanded tilde and/or shell variables in expression {0} to {1}."
            .format(repr(shortFile), repr(expandedFile)))

    if len(fileList) == 0:

        if root == "":
            logging.warning("No files found matching {0}."
                            .format(repr(shortFile)))

        else: # Fallback for older test-sets
            logging.warning("Perpaps old or invalid test-set. Trying fallback for {0}."
                            .format(repr(shortFileFallback)))
            fileList = getFileList(shortFileFallback)
            if len(fileList) != 0:
                logging.warning("Fallback has found some files for {0}."
                            .format(repr(shortFileFallback)))

    return fileList


class Column:
    """
    The class Column sets text, title and numberOfDigits of a column.
    """

    def __init__(self, columnTag):
        # get text
        self.text = columnTag.text

        # get title (title is optional, default: get text)
        self.title = columnTag.get("title", self.text)

        # get number of digits behind comma
        self.numberOfDigits = columnTag.get("numberOfDigits")


class OutputHandler:
    """
    The class OutputHandler manages all outputs to the terminal and to files.
    """

    def __init__(self, benchmark):
        """
        The constructor of OutputHandler creates the folder to store logfiles
        and collects information about the benchmark and the computer.
        """

        self.benchmark = benchmark
        self.statistics = Statistics()

        # create folder for file-specific log-files.
        # if the folder exists, it will be used.
        # if there are files in the folder (with the same name than the testfiles),
        # they will be OVERWRITTEN without a message!
        self.logFolder = OUTPUT_PATH + self.benchmark.name + "." + self.benchmark.date + ".logfiles/"
        if not os.path.isdir(self.logFolder):
            os.makedirs(self.logFolder)

        # get information about computer
        (opSystem, cpuModel, numberOfCores, maxFrequency, memory) = self.getSystemInfo()
        version = self.getVersion(self.benchmark.tool)

        memlimit = None
        timelimit = None
        if (resource.RLIMIT_AS in self.benchmark.rlimits):
            memlimit = str(self.benchmark.rlimits[resource.RLIMIT_AS][0] // 1024 // 1024) + " MB"
        if (resource.RLIMIT_CPU in self.benchmark.rlimits):
            timelimit = str(self.benchmark.rlimits[resource.RLIMIT_CPU][0]) + " s"

        self.storeHeaderInXML(version, memlimit, timelimit, opSystem, cpuModel,
                              numberOfCores, maxFrequency, memory)
        self.writeHeaderToLog(version, memlimit, timelimit, opSystem, cpuModel,
                              numberOfCores, maxFrequency, memory)

        # write columntitles of tests in CSV-files, this overwrites existing files
        self.CSVFiles = dict()
        CSVLine = CSV_SEPARATOR.join(
                      ["sourcefile", "status", "cputime", "walltime"] \
                    + [column.title for column in self.benchmark.columns])
        for test in benchmark.tests:
            if options.testRunOnly is None \
                    or options.testRunOnly == test.name:
                CSVFileName = self.getFileName(test.name, "csv")
                self.CSVFiles[CSVFileName] = FileWriter(CSVFileName, CSVLine + "\n")


    def storeHeaderInXML(self, version, memlimit, timelimit, opSystem,
                         cpuModel, numberOfCores, maxFrequency, memory):

        # store benchmarkInfo in XML
        self.XMLHeader = ET.Element("test",
                    {"benchmarkname": self.benchmark.name, "date": self.benchmark.date,
                     "tool": self.getToolnameForPrinting(), "version": version})
        if memlimit is not None:
            self.XMLHeader.set("memlimit", memlimit)
        if timelimit is not None:
            self.XMLHeader.set("timelimit", timelimit)

        # store systemInfo in XML
        osElem = ET.Element("os", {"name": opSystem})
        cpuElem = ET.Element("cpu",
            {"model": cpuModel, "cores": numberOfCores, "frequency" : maxFrequency})
        ramElem = ET.Element("ram", {"size": memory})

        systemInfo = ET.Element("systeminfo")
        systemInfo.append(osElem)
        systemInfo.append(cpuElem)
        systemInfo.append(ramElem)
        self.XMLHeader.append(systemInfo)

        # store columnTitles in XML
        columntitlesElem = ET.Element("columns")
        columntitlesElem.append(ET.Element("column", {"title": "status"}))
        columntitlesElem.append(ET.Element("column", {"title": "cputime"}))
        columntitlesElem.append(ET.Element("column", {"title": "walltime"}))
        for column in self.benchmark.columns:
            columnElem = ET.Element("column", {"title": column.title})
            columntitlesElem.append(columnElem)
        self.XMLHeader.append(columntitlesElem)


    def writeHeaderToLog(self, version, memlimit, timelimit, opSystem,
                         cpuModel, numberOfCores, maxFrequency, memory):
        """
        This method writes information about benchmark and system into TXTFile.
        """

        columnWidth = 20
        simpleLine = "-" * (60) + "\n\n"

        header = "   BENCHMARK INFORMATION\n"\
                + "benchmark:".ljust(columnWidth) + self.benchmark.name + "\n"\
                + "date:".ljust(columnWidth) + self.benchmark.date + "\n"\
                + "tool:".ljust(columnWidth) + self.getToolnameForPrinting()\
                + " " + version + "\n"

        if memlimit is not None:
            header += "memlimit:".ljust(columnWidth) + memlimit + "\n"
        if timelimit is not None:
            header += "timelimit:".ljust(columnWidth) + timelimit + "\n"
        header += simpleLine

        systemInfo = "   SYSTEM INFORMATION\n"\
                + "os:".ljust(columnWidth) + opSystem + "\n"\
                + "cpu:".ljust(columnWidth) + cpuModel + "\n"\
                + "- cores:".ljust(columnWidth) + numberOfCores + "\n"\
                + "- max frequency:".ljust(columnWidth) + maxFrequency + "\n"\
                + "ram:".ljust(columnWidth) + memory + "\n"\
                + simpleLine

        # write to file
        self.TXTFile = FileWriter(self.getFileName(None, "txt"), header + systemInfo)


    def getToolnameForPrinting(self):
        tool = self.benchmark.tool.lower()
        names = {'cpachecker': 'CPAchecker',
                 'cbmc'      : 'CBMC',
                 'satabs'    : 'SatAbs',
                 'blast'     : 'BLAST',
                 'wolverine' : 'WOLVERINE',
                 'acsar'     : 'Acsar'}
        if tool in names:
            return names[tool]
        else:
            return str(self.benchmark.tool)

# this function only for development, currently unused
    def getVersionOfCPAchecker(self):
        '''
        get info about CPAchecker from local svn- or git-svn-directory
        '''
        version = ''
        exe = findExecutable("cpachecker", "scripts/cpa.sh")
        try:
            cpaFolder = subprocess.Popen(['which', exe],
                              stdout=subprocess.PIPE).communicate()[0].strip('\n')

            # try to get revision with SVN
            output = subprocess.Popen(['svn', 'info', cpaFolder],
                              stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT).communicate()[0]

            # parse output and get revision
            svnInfoList = [line for line in output.strip('\n').split('\n')
                           if ': ' in line]
            svnInfo = dict(tuple(str.split(': ')) for str in svnInfoList)

            if 'Revision' in svnInfo: # revision from SVN successful
                version = 'r' + svnInfo['Revision']

            else: # try to get revision with GIT-SVN
                output = subprocess.Popen(['git', 'svn', 'info'],
                                  stdout=subprocess.PIPE,
                                  stderr=subprocess.STDOUT).communicate()[0]

                # parse output and get revision
                svnInfoList = [line for line in output.strip('\n').split('\n')
                               if ': ' in line]
                svnInfo = dict(tuple(str.split(': ')) for str in svnInfoList)

                if 'Revision' in svnInfo: # revision from GIT-SVN successful
                    version = 'r' + svnInfo['Revision']

                else:
                    logging.warning('revision of CPAchecker could not be read.')
        except OSError:
            pass
        return version


    def getVersion(self, tool):
        """
        This function return a String representing the version of the tool.
        """

        version = ''
        if (tool == "cpachecker"):
            exe = findExecutable("cpachecker", "scripts/cpa.sh")
            version = subprocess.Popen([exe, '-help'],
                stdout=subprocess.PIPE).communicate()[0].splitlines()[0].split()[1].strip()

        elif (tool == "cbmc"):
            defaultExe = None
            if platform.machine() == "x86_64":
                defaultExe = "lib/native/x86_64-linux/cbmc"
            elif platform.machine() == "i386":
                defaultExe = "lib/native/x86-linux/cbmc"

            exe = findExecutable("cbmc", defaultExe)
            version = subprocess.Popen([exe, '--version'],
                              stdout=subprocess.PIPE).communicate()[0].strip()

        elif (tool == "satabs"):
            exe = findExecutable("satabs", None)
            version = subprocess.Popen([exe, '--version'],
                              stdout=subprocess.PIPE).communicate()[0].strip()

        elif (tool == "wolverine"):
            exe = findExecutable("wolverine", None)
            version = subprocess.Popen([exe, '--version'],
                              stdout=subprocess.PIPE).communicate()[0].split()[1].strip()

        elif (tool == "blast"):
            exe = findExecutable("pblast.opt", None)
            version = subprocess.Popen([exe],
                              stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT).communicate()[0][6:9]

        return decodeToString(version)


    def getSystemInfo(self):
        """
        This function returns some information about the computer.
        """

        # get info about OS
        import os
        (sysname, name, kernel, version, machine) = os.uname()
        opSystem = sysname + " " + kernel + " " + machine

        # get info about CPU
        cpuInfo = dict()
        maxFrequency = 'unknown'
        cpuInfoFilename = '/proc/cpuinfo'
        if os.path.isfile(cpuInfoFilename) and os.access(cpuInfoFilename, os.R_OK):
            cpuInfoFile = open(cpuInfoFilename, "r")
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
            frequencyInfoFile = open(freqInfoFilename, "r")
            maxFrequency = frequencyInfoFile.read().strip('\n')
            frequencyInfoFile.close()
            maxFrequency = str(int(maxFrequency) // 1000) + ' MHz'

        # get info about memory
        memInfo = dict()
        memInfoFilename = '/proc/meminfo'
        if os.path.isfile(memInfoFilename) and os.access(memInfoFilename, os.R_OK):
            memInfoFile = open(memInfoFilename, "r")
            memInfo = dict(tuple(str.split(': ')) for str in
                            memInfoFile.read()
                            .replace('\t', '')
                            .strip('\n').split('\n'))
            memInfoFile.close()
        memTotal = memInfo.get('MemTotal', 'unknown').strip()

        return (opSystem, cpuModel, numberOfCores, maxFrequency, memTotal)


    def outputBeforeTest(self, test):
        """
        The method outputBeforeTest() calculates the length of the
        first column for the output in terminal and stores information
        about the test in XML.
        @param test: current test with a list of testfiles
        """

        self.test = test
        numberOfTest = self.benchmark.tests.index(self.test) + 1

        if len(self.test.sourcefiles) == 1:
            logging.debug("test {0} consists of 1 sourcefile.".format(
                    numberOfTest))
        else:
            logging.debug("test {0} consists of {1} sourcefiles.".format(
                    numberOfTest, len(self.test.sourcefiles)))

        # length of the first column in terminal
        self.maxLengthOfFileName = 20
        for (sourcefile, _) in self.test.sourcefiles:
            self.maxLengthOfFileName = max(len(sourcefile), self.maxLengthOfFileName)

        # write testname to terminal
        numberOfFiles = len(self.test.sourcefiles)
        print("\nrunning test" + \
            (" '" + test.name + "'" if test.name is not None else "") + \
            ("     (1 file)" if numberOfFiles == 1
                        else "     ({0} files)".format(numberOfFiles)))

        # store testname and options in XML,
        # copy benchmarkinfo, limits, columntitles, systeminfo from XMLHeader
        self.testElem = self.getCopyOfXMLElem(self.XMLHeader)
        testOptions = mergeOptions(self.benchmark.options, self.test.options)
        self.testElem.set("options", " ".join(toSimpleList(testOptions)))
        if self.test.name is not None:
            self.testElem.set("name", self.test.name)

        # write information about the test into TXTFile
        self.writeTestInfoToLog()


    def outputForSkippingTest(self, test):
        '''
        This function writes a simple message to terminal and logfile,
        when a test is skipped.
        There is no message about skipping a test in the xml-file.
        '''

        # print to terminal
        print("\nskipping test" + \
            (" '" + test.name + "'" if test.name is not None else ""))

        # write into TXTFile
        numberOfTest = self.benchmark.tests.index(test) + 1
        testInfo = "\n\n"
        if test.name is not None:
            testInfo += test.name + "\n"
        testInfo += "test {0} of {1}: skipped\n".format(
                numberOfTest, len(self.benchmark.tests))
        self.TXTFile.append(testInfo)


    def getCopyOfXMLElem(self, elem):
        '''
        This method returns a shallow copy of a XML-Element.
        This method is for compatibility with Python 2.6 or earlier..
        In Python 2.7 you can use  'copyElem = elem.copy()'  instead.
        '''

        copyElem = ET.Element(elem.tag, elem.attrib)
        for child in elem:
            copyElem.append(child)
        return copyElem


    def writeTestInfoToLog(self):
        """
        This method writes the information about a test into the TXTFile.
        """

        numberOfTest = self.benchmark.tests.index(self.test) + 1
        testOptions = mergeOptions(self.benchmark.options, self.test.options)
        if self.test.name is None:
            testInfo = ""
        else:
            testInfo = self.test.name + "\n"
        testInfo += "test {0} of {1} with options: {2}\n\n".format(
                numberOfTest, len(self.benchmark.tests),
                " ".join(toSimpleList(testOptions)))

        titleLine = self.createOutputLine("sourcefile", "status", "cpu time",
                            "wall time", self.benchmark.columns, True)

        self.test.simpleLine = "-" * (len(titleLine)) + "\n"

        # write into TXTFile
        self.TXTFile.append("\n\n" + testInfo + titleLine + "\n" + self.test.simpleLine)


    def outputBeforeRun(self, sourcefile, currentOptions):
        """
        The method outputBeforeRun() prints the name of a file to terminal.
        @param sourcefile: the name of a sourcefile
        """

        options = " ".join(currentOptions)
        logging.debug("I'm running '{0} {1} {2}'.".format(
            self.getToolnameForPrinting(), options, sourcefile))

        # output in terminal
        sys.stdout.write(time.strftime("%H:%M:%S", time.localtime()) \
            + '   ' + sourcefile.ljust(self.maxLengthOfFileName + 4))
        sys.stdout.flush()


    def outputAfterRun(self, sourcefile, fileOptions, status,
                       cpuTimeDelta, wallTimeDelta, output, args):
        """
        The method outputAfterRun() prints filename, result, time and status
        of a test to terminal and stores all data in XML
        The output is written to a file-specific log-file.
        """

        # format times, type is changed from float to string!
        cpuTimeDelta = self.formatNumber(cpuTimeDelta, TIME_PRECISION)
        wallTimeDelta = self.formatNumber(wallTimeDelta, TIME_PRECISION)

        # format numbers, numberOfDigits is optional, so it can be None
        for column in self.benchmark.columns:
            if column.numberOfDigits is not None:

                # if the number ends with "s" or another letter, remove it
                if (not column.value.isdigit()) and column.value[-2:-1].isdigit():
                    column.value = column.value[:-1]

                try:
                    floatValue = float(column.value)
                    column.value = self.formatNumber(floatValue, column.numberOfDigits)
                except ValueError: # if value is no float, don't format it
                    pass

        # write output to file-specific log-file
        logFileName = self.logFolder
        if self.test.name is not None:
            logFileName += self.test.name + "."
        logFileName += os.path.basename(sourcefile) + ".log"
        logFile = FileWriter(logFileName, ' '.join(args))
        logFile.append('\n\n\n' + '-'*80 + '\n\n\n')
        logFile.append(output)

        # output in terminal/console
        statusRelation = self.isCorrectResult(sourcefile, status)
        if USE_COLORS and sys.stdout.isatty(): # is terminal, not file
            statusStr = COLOR_DIC[statusRelation].format(status.ljust(8))
        else:
            statusStr = status.ljust(8)
        print(statusStr + cpuTimeDelta.rjust(8) + wallTimeDelta.rjust(8))

        # store filename, status, times, columns in XML
        fileElem = ET.Element("sourcefile", {"name": sourcefile})
        if len(fileOptions) != 0:
            fileElem.set("options", " ".join(toSimpleList(mergeOptions(fileOptions))))
        fileElem.append(ET.Element("column", {"title": "status", "value": status}))
        fileElem.append(ET.Element("column", {"title": "cputime", "value": cpuTimeDelta}))
        fileElem.append(ET.Element("column", {"title": "walltime", "value": wallTimeDelta}))

        for column in self.benchmark.columns:
            fileElem.append(ET.Element("column",
                    {"title": column.title, "value": column.value}))

        self.testElem.append(fileElem)

        # write resultline in TXTFile
        resultline = self.createOutputLine(sourcefile, status,
                    cpuTimeDelta, wallTimeDelta, self.benchmark.columns, False)
        self.TXTFile.append(resultline + "\n")

        # write columnvalues of the test into CSVFile
        CSVLine = CSV_SEPARATOR.join(
                  [sourcefile, status, cpuTimeDelta, wallTimeDelta] \
                + [column.value for column in self.benchmark.columns])
        self.CSVFiles[self.getFileName(self.test.name, "csv")].append(CSVLine + "\n")

        self.statistics.addResult(statusRelation)


    def outputAfterTest(self, cpuTimeTest, wallTimeTest):
        """
        The method outputAfterTest() stores the times of a test in XML.
        @params cpuTimeTest, wallTimeTest: times of the test
        """

        # format time, type is changed from float to string!
        cpuTimeTest = self.formatNumber(cpuTimeTest, TIME_PRECISION)
        wallTimeTest = self.formatNumber(wallTimeTest, TIME_PRECISION)

        # store testtime in XML
        timesElem = ET.Element("time", {"cputime": cpuTimeTest, "walltime": wallTimeTest})
        self.testElem.append(timesElem)

        # write XML-file
        FileWriter(self.getFileName(self.test.name, "xml"), XMLtoString(self.testElem))

        # write endline into TXTFile
        numberOfFiles = len(self.test.sourcefiles)
        numberOfTest = self.benchmark.tests.index(self.test) + 1
        if numberOfFiles == 1:
            endline = ("test {0} consisted of 1 sourcefile.".format(numberOfTest))
        else:
            endline = ("test {0} consisted of {1} sourcefiles.".format(
                    numberOfTest, numberOfFiles))

        endline = self.createOutputLine(endline, "done", cpuTimeTest,
                             wallTimeTest, [], False)

        self.TXTFile.append(self.test.simpleLine + endline + "\n")


    def isCorrectResult(self, filename, status):
        '''
        this function return a string,
        that shows the relation between status and file.
        '''
        status = status.lower()
        isSafeFile = not self.containsAny(filename.lower(), BUG_SUBSTRING_LIST)

        if status == 'safe':
            if isSafeFile:
                return "correctSafe"
            else:
                return "wrongSafe"
        elif status == 'unsafe':
            if isSafeFile:
                return "wrongUnsafe"
            else:
                return "correctUnsafe"
        else:
                 return "unknown"


    def containsAny(self, text, list):
        '''
        This function returns True, iff any string in list is a substring of text.
        '''
        for elem in list:
            if elem in text:
                return True
        return False


    def createOutputLine(self, sourcefile, status, cpuTimeDelta, wallTimeDelta, columns, isFirstLine):
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

        outputLine = sourcefile.ljust(self.maxLengthOfFileName + 4) + \
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


    def formatNumber(self, number, numberOfDigits):
            """
            The function formatNumber() return a string-representation of a number
            with a number of digits after the decimal separator.
            If the number has more digits, it is rounded.
            If the number has less digits, zeros are added.

            @param number: the number to format
            @param digits: the number of digits
            """

            return "%.{0}f".format(numberOfDigits) % number


    def getFileName(self, testname, fileExtension):
        '''
        This function returns the name of the file of a test
        with an extension ("txt", "xml", "csv").
        '''

        fileName = OUTPUT_PATH + self.benchmark.name + "." \
                    + self.benchmark.date + ".results."

        if testname is not None:
            fileName += testname + "."

        return fileName + fileExtension


class Statistics:

    def __init__(self):
        self.dic = {"counter": 0,
                    "correctSafe": 0,
                    "correctUnsafe": 0,
                    "unknown": 0,
                    "wrongUnsafe": 0,
                    "wrongSafe": 0}


    def addResult(self, statusRelation):
        self.dic["counter"] += 1
        assert statusRelation in self.dic
        self.dic[statusRelation] += 1


    def printToTerminal(self):
        print ('\n'.join(['\nStatistics:' + str(self.dic["counter"]).rjust(13) + ' Files',
                 '    correct:        ' + str(self.dic["correctSafe"] + \
                                              self.dic["correctUnsafe"]).rjust(4),
                 '    unknown:        ' + str(self.dic["unknown"]).rjust(4),
                 '    false positives:' + str(self.dic["wrongUnsafe"]).rjust(4) + \
                 '        (file is safe, result is unsafe)',
                 '    false negatives:' + str(self.dic["wrongSafe"]).rjust(4) + \
                 '        (file is unsafe, result is safe)',
                 '']))


def getOptions(optionsTag):
    '''
    This function searches for options in a tag
    and returns a list with tuples of (name, value).
    '''
    return [(option.get("name"), option.text)
               for option in optionsTag.findall("option")]


def mergeOptions(benchmarkOptions, testOptions=[], fileOptions=[]):
    '''
    This function merges lists of optionpairs into one list of pairs.
    If a option is part of several lists,
    the option appears in the list several times.
    '''

    currentOptions = []

    # copy global options
    currentOptions.extend(benchmarkOptions)

    # insert testOptions
    currentOptions.extend(testOptions)

    # insert fileOptions
    currentOptions.extend(fileOptions)

    return currentOptions


def toSimpleList(listOfPairs):
    '''
    This function converts a list of pairs to a list.
    Each pair of key and value is divided into 2 listelements.
    All "None"-values are removed.
    '''
    simpleList = []
    for (key, value) in listOfPairs:
        simpleList.append(key)
        if value is not None:
            simpleList.append(value)
    return simpleList


def XMLtoString(elem):
        """
        Return a pretty-printed XML string for the Element.
        """
        from xml.dom import minidom
        rough_string = ET.tostring(elem, 'utf-8')
        reparsed = minidom.parseString(rough_string)
        return reparsed.toprettyxml(indent="  ")


def decodeToString(toDecode):
    """
    This function is needed for Python 3,
    because a subprocess can return bytes instead of a string.
    """
    try: 
        return toDecode.decode('utf-8')
    except AttributeError: # bytesToDecode was of type string before
        return toDecode


class FileWriter:
     """
     The class FileWrtiter is a wrapper for writing content into a file.
     """

     def __init__(self, filename, content):
         """
         The constructor of FileWriter creates the file.
         If the file exist, it will be OVERWRITTEN without a message!
         """

         self.__filename = filename
         file = open(self.__filename, "w")
         file.write(content)
         file.close()

     def append(self, content):
         file = open(self.__filename, "a")
         file.write(content)
         file.close()


def findExecutable(program, default):
    def isExecutable(programPath):
        return os.path.isfile(programPath) and os.access(programPath, os.X_OK)

    dirs = os.environ['PATH'].split(os.pathsep)
    dirs.append(".")

    for dir in dirs:
        name = os.path.join(dir, program)
        if isExecutable(name):
            return name

    if default is not None and isExecutable(default):
        return default

    sys.exit("ERROR: Could not find %s executable" % program)


def killSubprocess(process):
    '''
    this function kills the process and the children in its group.
    '''
    os.killpg(process.pid, signal.SIGTERM)


def run(args, rlimits):
    args = [os.path.expandvars(arg) for arg in args]
    args = [os.path.expanduser(arg) for arg in args]

    def preSubprocess():
        os.setpgrp() # make subprocess to group-leader
        for rsrc in rlimits:
            resource.setrlimit(rsrc, rlimits[rsrc])

    ru_before = resource.getrusage(resource.RUSAGE_CHILDREN)
    wallTimeBefore = time.time()

    try:
        global p
        p = subprocess.Popen(args,
                             stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                             preexec_fn=preSubprocess)
    except OSError:
        logging.critical("I caught an OSError. Assure that the directory "
                         + "containing the tool to be benchmarked is included "
                         + "in the PATH environment variable or an alias is set.")
        sys.exit("A critical exception caused me to exit non-gracefully. Bye.")

    # if rlimit does not work, a seperate Timer is started to kill the subprocess,
    # Timer has 10 seconds 'overhead'
    from threading import Timer
    if (resource.RLIMIT_CPU in rlimits):
        timelimit = rlimits[resource.RLIMIT_CPU][0]
        global timer # "global" for KeyboardInterrupt from user
        timer = Timer(timelimit + 10, killSubprocess, [p])
        timer.start()

    output = p.communicate()[0]
    output = decodeToString(output)
    returncode = p.wait()

    if (0 in rlimits) and timer.isAlive():
        timer.cancel()

    wallTimeAfter = time.time()
    wallTimeDelta = wallTimeAfter - wallTimeBefore
    ru_after = resource.getrusage(resource.RUSAGE_CHILDREN)
    cpuTimeDelta = (ru_after.ru_utime + ru_after.ru_stime)\
        - (ru_before.ru_utime + ru_before.ru_stime)

    logging.debug("My subprocess returned returncode {0}.".format(returncode))
    return (returncode, output, cpuTimeDelta, wallTimeDelta)


def run_cbmc(options, sourcefile, columns, rlimits):
    if ("--xml-ui" not in options):
        options = options + ["--xml-ui"]

    defaultExe = None
    if platform.machine() == "x86_64":
        defaultExe = "lib/native/x86_64-linux/cbmc"
    elif platform.machine() == "i386":
        defaultExe = "lib/native/x86-linux/cbmc"

    exe = findExecutable("cbmc", defaultExe)
    args = [exe] + options + [sourcefile]
    (returncode, output, cpuTimeDelta, wallTimeDelta) = run(args, rlimits)

    #an empty tag cannot be parsed into a tree
    output = output.replace("<>", "<emptyTag>")
    output = output.replace("</>", "</emptyTag>")

    if ((returncode == 0) or (returncode == 10)):
        try:
            tree = ET.fromstring(output)
            status = tree.findtext('cprover-status', 'ERROR')
        except ExpatError as e:
            status = 'ERROR'
            sys.stdout.write("Error parsing CBMC output: %s " % e)

        if status == "FAILURE":
            assert returncode == 10
            reason = tree.find('goto_trace').find('failure').findtext('reason')
            if 'unwinding assertion' in reason:
                status = "UNKNOWN"
            else:
                status = "UNSAFE"
        elif status == "SUCCESS":
            assert returncode == 0
            if "--no-unwinding-assertions" in options:
                status = "UNKNOWN"
            else:
                status = "SAFE"

    elif returncode == -9:
        status = "TIMEOUT"
    elif returncode == 134:
        status = "ABORTED"
    elif returncode == 137:
        status = "KILLED BY SIGNAL 9"
    elif returncode == 143:
        status = "KILLED"
    else:
        status = "ERROR ({0})".format(returncode)

    return (status, cpuTimeDelta, wallTimeDelta, output, args)


def run_satabs(options, sourcefile, columns, rlimits):
    exe = findExecutable("satabs", None)
    args = [exe] + options + [sourcefile]

    def doNothing(signum, frame):
        pass

    # on timeout of rlimit the signal SIGTERM is thrown, catch and ignore it
    signal.signal(signal.SIGTERM, doNothing)

    (returncode, output, cpuTimeDelta, wallTimeDelta) = run(args, rlimits)

    # reset signal-handler
    signal.signal(signal.SIGTERM, signal_handler_ignore)

    if "VERIFICATION SUCCESSFUL" in output:
        assert returncode == 0
        status = "SAFE"
    elif "VERIFICATION FAILED" in output:
        assert returncode == 10
        status = "UNSAFE"
    elif returncode == -9:
        status = "TIMEOUT"
    elif returncode == -6:
        if "Assertion `!counterexample.steps.empty()' failed" in output:
            status = 'COUNTEREXAMPLE FAILED' # TODO: other status?
        else:
            status = "OUT OF MEMORY"
    elif returncode == 1 and "PARSING ERROR" in output:
        status = "PARSING ERROR"
    else:
        status = "FAILURE"
    return (status, cpuTimeDelta, wallTimeDelta, output, args)


def run_wolverine(options, sourcefile, columns, rlimits):
    exe = findExecutable("wolverine", None)
    args = [exe] + options + [sourcefile]
    (returncode, output, cpuTimeDelta, wallTimeDelta) = run(args, rlimits)
    if "VERIFICATION SUCCESSFUL" in output:
        assert returncode == 0
        status = "SAFE"
    elif "VERIFICATION FAILED" in output:
        assert returncode == 10
        status = "UNSAFE"
    elif returncode == -9:
        status = "TIMEOUT"
    elif returncode == -6 or (returncode == 6 and "Out of memory" in output):
        status = "OUT OF MEMORY"
    elif returncode == 6 and "PARSING ERROR" in output:
        status = "PARSING ERROR"
    else:
        status = "FAILURE"
    return (status, cpuTimeDelta, wallTimeDelta, output, args)


def run_acsar(options, sourcefile, columns, rlimits):
    exe = findExecutable("acsar", None)

    # create tmp-files for acsar, acsar needs special error-labels
    prepSourcefile = prepareSourceFileForAcsar(sourcefile)

    if ("--mainproc" not in options):
        options = options + ["--mainproc", "main"]

    args = [exe] + ["--file"] + [prepSourcefile] + options

    (returncode, output, cpuTimeDelta, wallTimeDelta) = run(args, rlimits)
    if "syntax error" in output:
        status = "SYNTAX ERROR"

    elif "runtime error" in output:
        status = "RUNTIME ERROR"

    elif "error while loading shared libraries:" in output:
        status = "LIBRARY ERROR"

    elif "can not be used as a root procedure because it is not defined" in output:
        status = "NO MAIN"

    elif "For Error Location <<ERROR_LOCATION>>: I don't Know " in output:
        status = "TIMEOUT"

    elif "received signal 6" in output:
        status = "ABORT"

    elif "received signal 11" in output:
        status = "SEGFAULT"

    elif "received signal 15" in output:
        status = "KILLED"

    elif "Error Location <<ERROR_LOCATION>> is not reachable" in output:
        status = "SAFE"

    elif "Error Location <<ERROR_LOCATION>> is reachable via the following path" in output:
        status = "UNSAFE"

    else:
        status = "UNKNOWN"

    # delete tmp-files
    import os
    os.remove(prepSourcefile)

    return (status, cpuTimeDelta, wallTimeDelta, output, args)


def prepareSourceFileForAcsar(sourcefile):
    content = open(sourcefile, "r").read()
    content = content.replace(
        "ERROR;", "ERROR_LOCATION;").replace(
        "ERROR:", "ERROR_LOCATION:").replace(
        "errorFn();", "goto ERROR_LOCATION; ERROR_LOCATION:;")
    newFilename = sourcefile + "_acsar.c"
    preparedFile = FileWriter(newFilename, content)
    return newFilename


# the next 3 functions are for imaginary tools, that return special results,
# perhaps someone can use these function again someday,
# to use them you need a normal benchmark-xml-file 
# with the tool and sourcefiles, however options are ignored
def run_safe(options, sourcefile, columns, rlimits):
    args = ['safe'] + options + [sourcefile]
    (returncode, output, cpuTimeDelta, wallTimeDelta) = (0, 'no output', 0, 0)
    return ('safe', cpuTimeDelta, wallTimeDelta, output, args)

def run_unsafe(options, sourcefile, columns, rlimits):
    args = ['unsafe'] + options + [sourcefile]
    (returncode, output, cpuTimeDelta, wallTimeDelta) = (0, 'no output', 0, 0)
    return ('unsafe', cpuTimeDelta, wallTimeDelta, output, args)

def run_random(options, sourcefile, columns, rlimits):
    args = ['random'] + options + [sourcefile]
    (returncode, output, cpuTimeDelta, wallTimeDelta) = (0, 'no output', 0, 0)
    from random import random
    status = 'safe' if random() < 0.5 else 'unsafe'
    return (status, cpuTimeDelta, wallTimeDelta, output, args)


def run_cpachecker(options, sourcefile, columns, rlimits):
    if ("-stats" not in options):
        options = options + ["-stats"]

    exe = findExecutable("cpachecker", "scripts/cpa.sh")
    args = [exe] + options + [sourcefile]
    (returncode, output, cpuTimeDelta, wallTimeDelta) = run(args, rlimits)

    status = getCPAcheckerStatus(returncode, output, rlimits, cpuTimeDelta)
    getCPAcheckerColumns(output, columns)

    return (status, cpuTimeDelta, wallTimeDelta, output, args)


def getCPAcheckerStatus(returncode, output, rlimits, cpuTimeDelta):
    """
    @param returncode: code returned by CPAchecker
    @param output: the output of CPAchecker
    @return: status of CPAchecker after running a testfile
    """

    def isOutOfMemory(line):
        return ('java.lang.OutOfMemoryError' in line
             or 'std::bad_alloc'             in line # C++ out of memory exception
             or 'Cannot allocate memory'     in line
             or line.startswith('out of memory')
             )

    if returncode == 0:
        status = None

    elif returncode == -6:
        status = "ABORTED (probably by Mathsat)"

    elif returncode == -9:
        if resource.RLIMIT_CPU in rlimits:
            limit = rlimits.get(resource.RLIMIT_CPU)[0]
        else:
            limit = float('inf')

        if cpuTimeDelta > limit*0.99:
            status = 'TIMEOUT'
        else:
            status = "KILLED BY SIGNAL 9"

    elif returncode == (128+15):
        status = "KILLED"

    else:
        status = "ERROR ({0})".format(returncode)

    for line in output.splitlines():
        if isOutOfMemory(line):
            status = 'OUT OF MEMORY'
        elif 'SIGSEGV' in line:
            status = 'SEGMENTATION FAULT'
        elif (returncode == 0 or returncode == 1) and ('Exception' in line):
            status = 'EXCEPTION'
        elif 'Could not reserve enough space for object heap' in line:
            status = 'JAVA HEAP ERROR'
        elif (status is None) and line.startswith('Verification result: '):
            line = line[21:].strip()
            if line.startswith('SAFE'):
                status = 'SAFE'
            elif line.startswith('UNSAFE'):
                status = 'UNSAFE'
            else:
                status = 'UNKNOWN'
        if (status is None) and line.startswith('#Test cases computed:'):
            status = 'OK'
    if status is None:
        status = "UNKNOWN"
    return status


def getCPAcheckerColumns(output, columns):
    """
    The method getCPAcheckerColumns() searches the columnvalues in the output
    and adds the values to the column-objects.
    If a value is not found, the value is set to "-".
    @param output: the output of CPAchecker
    @param columns: a list with columns
    """

    for column in columns:

        # search for the text in output and get its value,
        # stop after the first line, that contains the searched text
        column.value = "-" # default value
        for line in output.splitlines():
            if column.text in line:
                startPosition = line.find(':') + 1
                endPosition = line.find('(') # bracket maybe not found -> (-1)
                if (endPosition == -1):
                    column.value = line[startPosition:].strip()
                else:
                    column.value = line[startPosition: endPosition].strip()
                break


def run_blast(options, sourcefile, columns, rlimits):
    exe = findExecutable("pblast.opt", None)
    args = [exe] + options + [sourcefile]
    (returncode, output, cpuTimeDelta, wallTimeDelta) = run(args, rlimits)

    status = "UNKNOWN"
    for line in output.splitlines():
        if line.startswith('Error found! The system is unsafe :-('):
            status = 'UNSAFE'
        elif line.startswith('No error found.  The system is safe :-)'):
            status = 'SAFE'
        elif (returncode == 2) and line.startswith('Fatal error: out of memory.'):
            status = 'OUT OF MEMORY'
        elif (returncode == 2) and line.startswith('Fatal error: exception Sys_error("Broken pipe")'):
            status = 'EXCEPTION'
        elif (returncode == 2) and line.startswith('Ack! The gremlins again!: Sys_error("Broken pipe")'):
            status = 'TIMEOUT'

    return (status, cpuTimeDelta, wallTimeDelta, output, args)


def runBenchmark(benchmarkFile):
    benchmark = Benchmark(benchmarkFile)

    assert benchmark.tool in ["cbmc", "satabs", "cpachecker", "blast", "acsar", "wolverine",
                              "safe", "unsafe", "random"]
    run_func = eval("run_" + benchmark.tool)

    if len(benchmark.tests) == 1:
        logging.debug("I'm benchmarking {0} consisting of 1 test.".format(repr(benchmarkFile)))
    else:
        logging.debug("I'm benchmarking {0} consisting of {1} tests.".format(
                repr(benchmarkFile), len(benchmark.tests)))

    outputHandler = OutputHandler(benchmark)

    for test in benchmark.tests:

        if options.testRunOnly is not None \
                and options.testRunOnly != test.name:
            outputHandler.outputForSkippingTest(test)

        else:
            # get times before test
            ruBefore = resource.getrusage(resource.RUSAGE_CHILDREN)
            wallTimeBefore = time.time()

            outputHandler.outputBeforeTest(test)

            for (sourcefile, fileOptions) in test.sourcefiles:

                currentOptions = toSimpleList(mergeOptions(
                                    benchmark.options, test.options, fileOptions))
                outputHandler.outputBeforeRun(sourcefile, currentOptions)

                # run test
                (status, cpuTimeDelta, wallTimeDelta, output, args) = \
                    run_func(currentOptions, sourcefile, benchmark.columns, benchmark.rlimits)

                outputHandler.outputAfterRun(sourcefile, fileOptions, status,
                                             cpuTimeDelta, wallTimeDelta, output, args)

            # get times after test
            wallTimeAfter = time.time()
            wallTimeTest = wallTimeAfter - wallTimeBefore
            ruAfter = resource.getrusage(resource.RUSAGE_CHILDREN)
            cpuTimeTest = (ruAfter.ru_utime + ruAfter.ru_stime)\
            - (ruBefore.ru_utime + ruBefore.ru_stime)

            outputHandler.outputAfterTest(cpuTimeTest, wallTimeTest)

    outputHandler.outputAfterBenchmark()


def main(argv=None):

    if argv is None:
        argv = sys.argv
    from optparse import OptionParser
    parser = OptionParser(usage="usage: %prog [OPTION]... [FILE]...\n\n" + \
        "INFO: documented example-files can be found in 'doc/examples'\n")

    parser.add_option("-d", "--debug",
                      action="store_true",
                      help="enable debug output")

    parser.add_option("-t", "--test", dest="testRunOnly",
                      help="run only a special TEST from xml-file",
                      metavar="TEST")

    global options
    (options, args) = parser.parse_args(argv)

    if len(args) < 2:
        parser.error("invalid number of arguments")
    if (options.debug):
        logging.basicConfig(format="%(asctime)s - %(levelname)s - %(message)s",
                            level=logging.DEBUG)
    else:
        logging.basicConfig(format="%(asctime)s - %(levelname)s - %(message)s")

    for arg in args[1:]:
        if not os.path.exists(arg) or not os.path.isfile(arg):
            parser.error("File {0} does not exist.".format(repr(arg)))

    for arg in args[1:]:
        logging.debug("Benchmark {0} is started.".format(repr(arg)))
        runBenchmark(arg)
        logging.debug("Benchmark {0} is done.".format(repr(arg)))

    logging.debug("I think my job is done. Have a nice day!")


def signal_handler_ignore(signum, frame):
    logging.warn('Received signal %d, ignoring it' % signum)

if __name__ == "__main__":
    # ignore SIGTERM
    signal.signal(signal.SIGTERM, signal_handler_ignore)
    try:
        sys.exit(main())
    except KeyboardInterrupt:

        try:
            timer.cancel() # Timer, that kills a subprocess after timelimit
        except NameError:
            pass # if no timer is defined, do nothing

        try:
            killSubprocess(p) # kill running tool
        except NameError:
            pass # if tool not running, do nothing

        interruptMessage = "\n\nscript was interrupted by user, some tests may not be done"
        logging.debug(interruptMessage)

        print(interruptMessage)

        pass

