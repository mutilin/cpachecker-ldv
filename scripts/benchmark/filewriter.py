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

import os
from . import util

class FileWriter:
    """
    The class FileWriter is a wrapper for writing content into a file.
    """

    def __init__(self, filename, content):
        """
        The constructor of FileWriter creates the file.
        If the file exist, it will be OVERWRITTEN without a message!
        """

        self.__filename = filename
        self.__needsRewrite = False
        self.__content = content

        # Open file with "w" at least once so it will be overwritten.
        util.writeFile(content, self.__filename)

    def append(self, newContent, keep=True):
        """
        Add content to the represented file.
        If keep is False, the new content will be forgotten during the next call
        to this method.
        """
        content = self.__content + newContent
        if keep:
            self.__content = content

        if self.__needsRewrite:
            """
            Replace the content of the file.
            A temporary file is used to avoid loss of data through an interrupt.
            """
            tmpFilename = self.__filename + ".tmp"

            util.writeFile(content, tmpFilename)

            os.rename(tmpFilename, self.__filename)
        else:
            with open(self.__filename, "a") as file:
                file.write(newContent)

        self.__needsRewrite = not keep

    def replace(self, newContent):
        # clear and append
        self.__content = ''
        self.__needsRewrite = True
        self.append(newContent)
