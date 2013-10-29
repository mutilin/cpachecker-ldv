import benchmark.tools.template
import benchmark.result as result

class Tool(benchmark.tools.template.BaseTool):
    """
    This tool is an imaginary tool that returns always UNSAFE.
    To use it you need a normal benchmark-xml-file
    with the tool and sourcefiles, however options are ignored.
    """

    def getExecutable(self):
        return '/bin/false'

    def getName(self):
        return 'AlwaysUnsafe'

    def getCmdline(self, executable, options, sourcefile):
        return [executable]

    def getStatus(self, returncode, returnsignal, output, isTimeout):
        return result.STR_FALSE