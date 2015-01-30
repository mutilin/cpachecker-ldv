import os
import subprocess

import benchmark.util as Util
import benchmark.tools.template
import benchmark.result as result

class Tool(benchmark.tools.template.BaseTool):

    def getExecutable(self):
        return Util.findExecutable('acsar')


    def getName(self):
        return 'Acsar'


    def getCmdline(self, executable, options, sourcefiles, propertyfile, rlimits):
        assert len(sourcefiles) == 1, "only one sourcefile supported"
        sourcefile = sourcefiles[0]

        # create tmp-files for acsar, acsar needs special error-labels
        self.prepSourcefile = self._prepareSourcefile(sourcefile)

        return [executable] + ["--file"] + [self.prepSourcefile] + options


    def _prepareSourcefile(self, sourcefile):
        content = open(sourcefile, "r").read()
        content = content.replace(
            "ERROR;", "ERROR_LOCATION;").replace(
            "ERROR:", "ERROR_LOCATION:").replace(
            "errorFn();", "goto ERROR_LOCATION; ERROR_LOCATION:;")
        newFilename = sourcefile + "_acsar.c"
        Util.writeFile(newFilename, content)
        return newFilename


    def getStatus(self, returncode, returnsignal, output, isTimeout):
        output = '\n'.join(output)
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
            status = result.STATUS_TRUE_PROP

        elif "Error Location <<ERROR_LOCATION>> is reachable via the following path" in output:
            status = result.STATUS_FALSE_REACH

        else:
            status = result.STATUS_UNKNOWN

        # delete tmp-files
        os.remove(self.prepSourcefile)

        return status
