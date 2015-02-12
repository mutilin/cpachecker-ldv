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
from __future__ import absolute_import, division, print_function, unicode_literals

import sys
sys.dont_write_bytecode = True # prevent creation of .pyc files

import itertools
import logging
import unittest

from .localexecution import _get_cpu_cores_per_run0


class TestCpuCoresPerRun(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.longMessage = True
        logging.disable(logging.CRITICAL)

    def assertValid(self, coreLimit, numOfThreads, expectedResult=None):
        result = _get_cpu_cores_per_run0(coreLimit, numOfThreads, *self.machine())
        if expectedResult:
            self.assertEqual(expectedResult, result, "Incorrect result for {} cores and {} threads.".format(coreLimit, numOfThreads))

    def assertInvalid(self, coreLimit, numOfThreads):
        self.assertRaises(SystemExit, _get_cpu_cores_per_run0, coreLimit, numOfThreads, *self.machine())

    def machine(self):
        """Create the necessary parameters of _get_cpu_cores_per_run0 for a specific machine."""
        core_count = self.cpus * self.cores
        allCpus = range(core_count)
        cores_of_package = {}
        ht_spread = core_count // 2
        for package in xrange(self.cpus):
            start = package * self.cores // (2 if self.ht else 1)
            end = (package+1) * self.cores // (2 if self.ht else 1)
            cores_of_package[package] = range(start, end)
            if self.ht:
                cores_of_package[package].extend(xrange(start + ht_spread, end + ht_spread))
        siblings_of_core = {}
        for core in allCpus:
            siblings_of_core[core] = [core]
        if self.ht:
            for core in allCpus:
                siblings_of_core[core].append((core + ht_spread) % core_count)
                siblings_of_core[core].sort()
        return (allCpus, cores_of_package, siblings_of_core)


    def test_singleThread(self):
        # test all possible coreLimits for a single thread
        core_count = self.cpus * self.cores
        if self.ht:
            # Creates list alternating between real core and hyper-threading core
            singleThread_assignment = list(itertools.chain(*zip(range(core_count // 2), range(core_count // 2, core_count))))
        else:
            singleThread_assignment = range(core_count)
        for coreLimit in xrange(1, core_count + 1):
            self.assertValid(coreLimit, 1, [sorted(singleThread_assignment[:coreLimit])])
        self.assertInvalid(core_count + 1, 1)

    # expected order in which cores are used for runs with coreLimit==1/2/3/4/8, used by the following tests
    # these fields should be filled in by subclasses to activate the corresponding tests
    # (same format as the expected return value by _get_cpu_cores_per_run)
    oneCore_assignment = None
    twoCore_assignment = None
    threeCore_assignment = None
    fourCore_assignment = None
    eightCore_assignment = None

    def test_oneCorePerRun(self):
        # test all possible numOfThread values for runs with one core
        maxThreads = self.cpus * self.cores
        self.assertInvalid(1, maxThreads + 1)
        if not self.oneCore_assignment:
            self.skipTest("Need result specified")
        for numOfThreads in xrange(1, maxThreads + 1):
            self.assertValid(1, numOfThreads, self.oneCore_assignment[:numOfThreads])

    def test_twoCoresPerRun(self):
        # test all possible numOfThread values for runs with two cores
        maxThreads = self.cpus * (self.cores // 2)
        self.assertInvalid(2, maxThreads + 1)
        if not self.twoCore_assignment:
            self.skipTest("Need result specified")
        for numOfThreads in xrange(1, maxThreads + 1):
            self.assertValid(2, numOfThreads, self.twoCore_assignment[:numOfThreads])

    def test_threeCoresPerRun(self):
        # test all possible numOfThread values for runs with three cores
        maxThreads = self.cpus * (self.cores // 3)
        self.assertInvalid(3, maxThreads + 1)
        if not self.threeCore_assignment:
            self.skipTest("Need result specified")
        for numOfThreads in xrange(1, maxThreads + 1):
            self.assertValid(3, numOfThreads, self.threeCore_assignment[:numOfThreads])

    def test_fourCoresPerRun(self):
        # test all possible numOfThread values for runs with four cores
        maxThreads = self.cpus * (self.cores // 4)
        self.assertInvalid(4, maxThreads + 1)
        if not self.fourCore_assignment:
            self.skipTest("Need result specified")
        for numOfThreads in xrange(1, maxThreads + 1):
            self.assertValid(4, numOfThreads, self.fourCore_assignment[:numOfThreads])

    def test_eightCoresPerRun(self):
        # test all possible numOfThread values for runs with eight cores
        maxThreads = self.cpus * (self.cores // 8)
        if not maxThreads:
            self.skipTest("Testing for runs that need to be split across CPUs is not implemented")
        self.assertInvalid(8, maxThreads + 1)
        if not self.eightCore_assignment:
            self.skipTest("Need result specified")
        for numOfThreads in xrange(1, maxThreads + 1):
            self.assertValid(8, numOfThreads, self.eightCore_assignment[:numOfThreads])


class TestCpuCoresPerRun_singleCPU(TestCpuCoresPerRun):
    cpus = 1
    cores = 8
    ht = False

    oneCore_assignment   = map(lambda x: [x], range(8))
    twoCore_assignment   = [[0, 1], [2, 3], [4, 5], [6, 7]]
    threeCore_assignment = [[0, 1, 2], [3, 4, 5]]
    fourCore_assignment  = [[0, 1, 2, 3], [4, 5, 6, 7]]
    eightCore_assignment = [range(8)]

    def test_singleCPU_invalid(self):
        self.assertInvalid(2, 5)
        self.assertInvalid(5, 2)
        self.assertInvalid(3, 3)


class TestCpuCoresPerRun_singleCPU_HT(TestCpuCoresPerRun_singleCPU):
    ht = True

    twoCore_assignment   = [[0, 4], [1, 5], [2, 6], [3, 7]]
    threeCore_assignment = [[0, 1, 4], [2, 3, 6]]
    fourCore_assignment  = [[0, 1, 4, 5], [2, 3, 6, 7]]


class TestCpuCoresPerRun_dualCPU_HT(TestCpuCoresPerRun):
    cpus = 2
    cores = 16
    ht = True

    oneCore_assignment = map(lambda x: [x], [0, 8, 1, 9, 2, 10, 3, 11, 4, 12, 5, 13, 6, 14, 7, 15, 16, 24, 17, 25, 18, 26, 19, 27, 20, 28, 21, 29, 22, 30, 23, 31])

    twoCore_assignment = [[0, 16], [8, 24], [1, 17], [9, 25], [2, 18], [10, 26], [3, 19], [11, 27], [4, 20], [12, 28], [5, 21], [13, 29], [6, 22], [14, 30], [7, 23], [15, 31]]

    # Note: the core assignment here is non-uniform, the last two threads are spread over three physical cores
    # Currently, the assignment algorithm cannot do better for odd coreLimits,
    # but this affects only cases where physical cores are split between runs, which is not recommended anyway.
    threeCore_assignment = [[0, 1, 16], [8, 9, 24], [2, 3, 18], [10, 11, 26], [4, 5, 20], [12, 13, 28], [6, 7, 22], [14, 15, 30], [17, 19, 21], [25, 27, 29]]

    fourCore_assignment = [[0, 1, 16, 17], [8, 9, 24, 25], [2, 3, 18, 19], [10, 11, 26, 27], [4, 5, 20, 21], [12, 13, 28, 29], [6, 7, 22, 23], [14, 15, 30, 31]]

    eightCore_assignment = [[0, 1, 2, 3, 16, 17, 18, 19], [8, 9, 10, 11, 24, 25, 26, 27], [4, 5, 6, 7, 20, 21, 22, 23], [12, 13, 14, 15, 28, 29, 30, 31]]

    def test_dualCPU_HT(self):
        self.assertValid(16, 2, [range(0, 8) + range(16, 24), range(8, 16) + range(24, 32)])

    def test_dualCPU_HT_invalid(self):
        self.assertInvalid(2, 17)
        self.assertInvalid(17, 2)
        self.assertInvalid(4, 9)
        self.assertInvalid(9, 4)
        self.assertInvalid(8, 5)
        self.assertInvalid(5, 8)


class TestCpuCoresPerRun_threeCPU(TestCpuCoresPerRun):
    cpus = 3
    cores = 5
    ht = False

    oneCore_assignment = map(lambda x: [x], [0, 5, 10, 1, 6, 11, 2, 7, 12, 3, 8, 13, 4, 9, 14])
    twoCore_assignment = [[0, 1], [5, 6], [10, 11], [2, 3], [7, 8], [12, 13]]
    threeCore_assignment = [[0, 1, 2], [5, 6, 7], [10, 11, 12]]
    fourCore_assignment = [[0, 1, 2, 3], [5, 6, 7, 8], [10, 11, 12, 13]]

    def test_threeCPU_invalid(self):
        self.assertInvalid(6, 2)

class TestCpuCoresPerRun_threeCPU_HT(TestCpuCoresPerRun):
    cpus = 3
    cores = 10
    ht = True

    oneCore_assignment = map(lambda x: [x], [0, 5, 10, 1, 6, 11, 2, 7, 12, 3, 8, 13, 4, 9, 14, 15, 20, 25, 16, 21, 26, 17, 22, 27, 18, 23, 28, 19, 24, 29])
    twoCore_assignment = [[0, 15], [5, 20], [10, 25], [1, 16], [6, 21], [11, 26], [2, 17], [7, 22], [12, 27], [3, 18], [8, 23], [13, 28], [4, 19], [9, 24], [14, 29]]
    threeCore_assignment = [[0, 1, 15], [5, 6, 20], [10, 11, 25], [2, 3, 17], [7, 8, 22], [12, 13, 27], [4, 16, 19], [9, 21, 24], [14, 26, 29]]
    fourCore_assignment = [[0, 1, 15, 16], [5, 6, 20, 21], [10, 11, 25, 26], [2, 3, 17, 18], [7, 8, 22, 23], [12, 13, 27, 28]]
    eightCore_assignment = [[0, 1, 2, 3, 15, 16, 17, 18], [5, 6, 7, 8, 20, 21, 22, 23], [10, 11, 12, 13, 25, 26, 27, 28]]

    def test_threeCPU_HT_invalid(self):
        self.assertInvalid(11, 2)

class TestCpuCoresPerRun_quadCPU_HT(TestCpuCoresPerRun):
    cpus = 4
    cores = 16
    ht = True

    def test_quadCPU_HT(self):
        self.assertValid(16, 4, [range(0, 8) + range(32, 40), range(8, 16) + range(40, 48), range(16, 24) + range(48, 56), range(24, 32) + range(56, 64)])

        # Just test that no exception occurs
        self.assertValid(1, 64)
        self.assertValid(64, 1)
        self.assertValid(2, 32)
        self.assertValid(32, 2)
        self.assertValid(3, 20)
        self.assertValid(16, 3)
        self.assertValid(4, 16)
        self.assertValid(16, 4)
        self.assertValid(5, 12)
        self.assertValid(8, 8)

    def test_quadCPU_HT_invalid(self):
        self.assertInvalid(2, 33)
        self.assertInvalid(33, 2)
        self.assertInvalid(3, 21)
        self.assertInvalid(17, 3)
        self.assertInvalid(4, 17)
        self.assertInvalid(17, 4)
        self.assertInvalid(5, 13)
        self.assertInvalid(9, 5)
        self.assertInvalid(6, 9)
        self.assertInvalid(9, 6)
        self.assertInvalid(7, 9)
        self.assertInvalid(9, 7)
        self.assertInvalid(8, 9)
        self.assertInvalid(9, 8)

        self.assertInvalid(9, 5)
        self.assertInvalid(6, 9)
        self.assertInvalid(10, 5)
        self.assertInvalid(6, 10)
        self.assertInvalid(11, 5)
        self.assertInvalid(6, 11)
        self.assertInvalid(12, 5)
        self.assertInvalid(6, 12)
        self.assertInvalid(13, 5)
        self.assertInvalid(5, 13)
        self.assertInvalid(14, 5)
        self.assertInvalid(5, 14)
        self.assertInvalid(15, 5)
        self.assertInvalid(5, 15)
        self.assertInvalid(16, 5)
        self.assertInvalid(5, 16)

# prevent execution of base class as its own test
del(TestCpuCoresPerRun)
