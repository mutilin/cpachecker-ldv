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
from . import util
import os


# CONSTANTS

CATEGORY_CORRECT = 'correct'
CATEGORY_WRONG   = 'wrong'
CATEGORY_UNKNOWN = 'unknown'
CATEGORY_ERROR   = 'error'

STR_TRUE = 'true'
STR_UNKNOWN = 'unknown'

STR_FALSE_LABEL =       'false(label)'
STR_FALSE_TERMINATION = 'false(termination)'
STR_PROP_DEREF =        'false(valid-deref)'
STR_PROP_FREE =         'false(valid-free)'
STR_PROP_MEMTRACK =     'false(valid-memtrack)'

STR_LIST = [STR_TRUE, STR_UNKNOWN, 
            STR_FALSE_LABEL, STR_FALSE_TERMINATION, 
            STR_PROP_DEREF, STR_PROP_FREE, STR_PROP_MEMTRACK]

# string searched in filenames to determine correct or incorrect status.
# use lower case! the dict contains assignments 'filename' --> 'status'
FALSE_SUBSTRINGS = {'_false-unreach-label':  STR_FALSE_LABEL,
                    '_false-termination':    STR_FALSE_TERMINATION,
                    '_false-valid-deref':    STR_PROP_DEREF,
                    '_false-valid-free':     STR_PROP_FREE,
                    '_false-valid-memtrack': STR_PROP_MEMTRACK
                   }

assert all('_false-' in k for k in FALSE_SUBSTRINGS.keys())


# Score values taken from http://sv-comp.sosy-lab.org/
SCORE_CORRECT_TRUE = 2
SCORE_CORRECT_FALSE = 1
SCORE_UNKNOWN = 0
SCORE_WRONG_FALSE = -4
SCORE_WRONG_TRUE = -8


def _statusesOfFile(filename):
    """
    This function returns all statuses of a file, 
    this is the list of false-properties in the filename.
    """
    statuses = [val for (key,val) in FALSE_SUBSTRINGS.items() if key in filename]
    #TODO: enable next line, when all sourcefiles are renamed
    #if not statuses: assert not '_false' in filename
    return statuses


def _statusesOfPropertyFile(propertyFile):
    assert propertyFile is None or os.path.isfile(propertyFile)
    
    if propertyFile is None:
        # if we have no prpfile, lets use default property 'error-label-reachability'
        return [STR_FALSE_LABEL]
    
    statuses = []
    with open(propertyFile) as f:
        content = f.read()
        assert 'CHECK' in content
        
        # TODO: should we switch to regex or line-based reading?
        if 'LTL(G ! label(' in content:
            statuses.append(STR_FALSE_LABEL)
        if 'LTL(F end)' in content:
            statuses.append(STR_FALSE_TERMINATION)
        if 'LTL(G valid-free)' in content:
            statuses.append(STR_PROP_FREE)
        if 'LTL(G valid-deref)' in content:
            statuses.append(STR_PROP_DEREF)
        if 'LTL(G valid-memtrack)' in content:
            statuses.append(STR_PROP_MEMTRACK)
        
        assert len(statuses) > 0
    return statuses


# the functions fileIsFalse and fileIsTrue are only used tocount files,
# not in any other logic. They should return complementary values.
def fileIsFalse(filename):
    return util.containsAny(filename, FALSE_SUBSTRINGS.keys())

def fileIsTrue(filename):
    return not util.containsAny(filename, FALSE_SUBSTRINGS.keys())


def getResultCategory(filename, status, propertyFile=None):
    '''
    This function return a string
    that shows the relation between status and file.
    '''
    fileStatuses = _statusesOfFile(filename)
    propertiesToCheck = _statusesOfPropertyFile(propertyFile)

    if status == STR_UNKNOWN:
        category = CATEGORY_UNKNOWN
    elif status in STR_LIST:
        if status == STR_TRUE and not fileStatuses:
            category = CATEGORY_CORRECT
        elif status in propertiesToCheck and status in fileStatuses:
            category = CATEGORY_CORRECT
        else:
            category = CATEGORY_WRONG
    else:
        category = CATEGORY_ERROR
    return category


def calculateScore(category, status):
    if category == CATEGORY_CORRECT:
        return SCORE_CORRECT_TRUE if status == STR_TRUE else SCORE_CORRECT_FALSE
    elif category == CATEGORY_WRONG:
        return SCORE_WRONG_TRUE if status == STR_TRUE else SCORE_WRONG_FALSE
    elif category in [CATEGORY_UNKNOWN, CATEGORY_ERROR]:
        return SCORE_UNKNOWN
    else:
        assert False, 'impossible category {0}'.format(category)
