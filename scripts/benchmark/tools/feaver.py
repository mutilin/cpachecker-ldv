import os

import benchmark.filewriter as filewriter
import benchmark.util as Util
import benchmark.tools.template
import benchmark.result as result

class Tool(benchmark.tools.template.BaseTool):

    def getExecutable(self):
        return Util.findExecutable('feaver_cmd')


    def getName(self):
        return 'Feaver'


    def getCmdline(self, executable, options, sourcefiles, propertyfile, rlimits):
        assert len(sourcefiles) == 1, "only one sourcefile supported"
        sourcefile = sourcefiles[0]
        
        # create tmp-files for feaver, feaver needs special error-labels
        self.prepSourcefile = _prepareSourcefile(sourcefile)

        return [executable] + ["--file"] + [self.prepSourcefile] + options


    def _prepareSourcefile(self, sourcefile):
        content = open(sourcefile, "r").read()
        content = content.replace("goto ERROR;", "assert(0);")
        newFilename = "tmp_benchmark_feaver.c"
        filewriter.writeFile(newFilename, content)
        return newFilename


    def getStatus(self, returncode, returnsignal, output, isTimeout):
        output = '\n'.join(output)
        if "collect2: ld returned 1 exit status" in output:
            status = "COMPILE ERROR"

        elif "Error (parse error" in output:
            status = "PARSE ERROR"

        elif "error: (\"model\":" in output:
            status = "MODEL ERROR"

        elif "Error: syntax error" in output:
            status = "SYNTAX ERROR"

        elif "error: " in output or "Error: " in output:
            status = "ERROR"

        elif "Error Found:" in output:
            status = result.STATUS_FALSE_REACH

        elif "No Errors Found" in output:
            status = result.STATUS_TRUE_PROP

        else:
            status = result.STATUS_UNKNOWN

        # delete tmp-files
        for tmpfile in [self.prepSourcefile, self.prepSourcefile[0:-1] + "M",
                     "_modex_main.spn", "_modex_.h", "_modex_.cln", "_modex_.drv",
                     "model", "pan.b", "pan.c", "pan.h", "pan.m", "pan.t"]:
            try:
                os.remove(tmpfile)
            except OSError:
                pass

        return status