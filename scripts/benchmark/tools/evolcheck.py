import logging
import os
import platform
import tempfile
import subprocess
import hashlib
import xml.etree.ElementTree as ET

import benchmark.util as Util
import benchmark.tools.template
import benchmark.result as result

class Tool(benchmark.tools.template.BaseTool):

    previousStatus = None

    def getExecutable(self):
        return Util.findExecutable('evolcheck_wrapper')


    def getVersion(self, executable):
        return subprocess.Popen([executable, '--version'],
                                stdout=subprocess.PIPE).communicate()[0].strip()

    def getName(self):
        return 'eVolCheck'

    def preprocessSourcefile(self, sourcefile):
        gotoCcExecutable      = Util.findExecutable('goto-cc')
        # compile with goto-cc to same file, bith '.cc' appended
        self.preprocessedFile = sourcefile + ".cc"

        subprocess.Popen([gotoCcExecutable,
                            sourcefile,
                            '-o',
                            self.preprocessedFile],
                          stdout=subprocess.PIPE).wait()

        return self.preprocessedFile


    def getCmdline(self, executable, options, sourcefile):
        sourcefile = self.preprocessSourcefile(sourcefile)

        # also append '.cc' to the predecessor-file
        if '--predecessor' in options :
            options[options.index('--predecessor') + 1] = options[options.index('--predecessor') + 1] + '.cc'

        return [executable] + [sourcefile] + options

    def getStatus(self, returncode, returnsignal, output, isTimeout):
        if not os.path.isfile(self.preprocessedFile):
            return 'ERROR (goto-cc)'

        status = None

        assertionHoldsFound         = False
        verificationSuccessfulFound = False
        verificationFailedFound     = False

        for line in output.splitlines():
            if 'A real bug found.' in line:
                status = result.STR_FALSE
            elif 'VERIFICATION SUCCESSFUL' in line:
                verificationSuccessfulFound = True
            elif 'VERIFICATION FAILED' in line:
                verificationFailedFound = True
            elif 'ASSERTION(S) HOLD(S)' in line:
                assertionHoldsFound = True
            elif 'The program models are identical' in line:
                status = self.previousStatus
            elif 'Assertion(s) hold trivially.' in line:
                status = result.STR_TRUE

        if status is None:
            if verificationSuccessfulFound and not verificationFailedFound:
                status = result.STR_TRUE
            else:
                status = result.STR_UNKNOWN

        self.previousStatus = status

        return status
