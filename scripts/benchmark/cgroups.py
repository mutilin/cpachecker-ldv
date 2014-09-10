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
import shutil
import signal
import tempfile
import time

from . import util as Util

CGROUP_NAME_PREFIX='benchmark_'

ALL_KNOWN_SUBSYSTEMS = set(['cpuacct', 'cpuset', 'freezer', 'memory'])

def initCgroup(cgroupsParents, subsystem):
    """
    Initialize a cgroup subsystem.
    Call this before calling any other methods from this module for this subsystem.
    @param cgroupsParents: A dictionary with the cgroup mount points for each subsystem (filled by this method)
    @param subsystem: The subsystem to initialize
    """
    if not cgroupsParents:
        # first call to this method
        logging.debug('Analyzing /proc/mounts and /proc/self/cgroup for determining cgroups.')
        mounts = _findCgroupMounts()
        cgroups = _findOwnCgroups()

        for mountedSubsystem, mount in mounts.items():
            cgroupsParents[mountedSubsystem] = os.path.join(mount, cgroups[mountedSubsystem])

    if not subsystem in cgroupsParents:
        logging.warning(
'''Cgroup subsystem {0} not enabled.
Please enable it with "sudo mount -t cgroup none /sys/fs/cgroup".'''
            .format(subsystem)
            )
        cgroupsParents[subsystem] = None
        return

    cgroup = cgroupsParents[subsystem]
    logging.debug('My cgroup for subsystem {0} is {1}'.format(subsystem, cgroup))

    try: # only for testing?
        testCgroup = createCgroup(cgroupsParents, subsystem)[subsystem]
        removeCgroup(testCgroup)
    except OSError as e:
        logging.warning(
'''Cannot use cgroup hierarchy mounted at {0}, reason: {1}
If permissions are wrong, please run "sudo chmod o+wt \'{0}\'".'''
            .format(cgroup, e.strerror))
        cgroupsParents[subsystem] = None


def _findCgroupMounts():
    mounts = {}
    try:
        with open('/proc/mounts', 'rt') as mountsFile:
            for mount in mountsFile:
                mount = mount.split(' ')
                if mount[2] == 'cgroup':
                    mountpoint = mount[1]
                    options = mount[3]
                    for option in options.split(','):
                        if option in ALL_KNOWN_SUBSYSTEMS:
                            mounts[option] = mountpoint
    except IOError as e:
        logging.exception('Cannot read /proc/mounts')
    return mounts


def _findOwnCgroups():
    """
    Given a cgroup subsystem,
    find the cgroup in which this process is in.
    (Each process is in exactly cgroup in each hierarchy.)
    @return the path to the cgroup inside the hierarchy
    """
    ownCgroups = {}
    try:
        with open('/proc/self/cgroup', 'rt') as ownCgroupsFile:
            for ownCgroup in ownCgroupsFile:
                #each line is "id:subsystem,subsystem:path"
                ownCgroup = ownCgroup.strip().split(':')
                path = ownCgroup[2][1:] # remove leading /
                for subsystem in ownCgroup[1].split(','):
                    ownCgroups[subsystem] = path
    except IOError as e:
        logging.exception('Cannot read /proc/self/cgroup')
    return ownCgroups


def createCgroup(cgroupsParents, *subsystems):
    """
    Try to create a cgroup for each of the given subsystems.
    If multiple subsystems are available in the same hierarchy,
    a common cgroup for theses subsystems is used.
    @param subsystems: a list of cgroup subsystems
    @return a map from subsystem to cgroup for each subsystem where it was possible to create a cgroup
    """
    createdCgroupsPerSubsystem = {}
    createdCgroupsPerParent = {}
    for subsystem in subsystems:
        if not subsystem in cgroupsParents:
            initCgroup(cgroupsParents, subsystem)

        parentCgroup = cgroupsParents.get(subsystem)
        if not parentCgroup:
            # subsystem not enabled
            continue
        if parentCgroup in createdCgroupsPerParent:
            # reuse already created cgroup
            createdCgroupsPerSubsystem[subsystem] = createdCgroupsPerParent[parentCgroup]
            continue

        cgroup = tempfile.mkdtemp(prefix=CGROUP_NAME_PREFIX, dir=parentCgroup)
        createdCgroupsPerSubsystem[subsystem] = cgroup
        createdCgroupsPerParent[parentCgroup] = cgroup

        # add allowed cpus and memory to cgroup if necessary
        # (otherwise we can't add any tasks)
        try:
            shutil.copyfile(os.path.join(parentCgroup, 'cpuset.cpus'), os.path.join(cgroup, 'cpuset.cpus'))
            shutil.copyfile(os.path.join(parentCgroup, 'cpuset.mems'), os.path.join(cgroup, 'cpuset.mems'))
        except IOError:
            # expected to fail if cpuset subsystem is not enabled in this hierarchy
            pass

    return createdCgroupsPerSubsystem

def addTaskToCgroup(cgroup, pid):
    if cgroup:
        with open(os.path.join(cgroup, 'tasks'), 'w') as tasksFile:
            tasksFile.write(str(pid))

def killAllTasksInCgroup(cgroup):
    tasksFile = os.path.join(cgroup, 'tasks')

    for i, sig in enumerate([signal.SIGINT, signal.SIGTERM, signal.SIGKILL]): # Do several tries of killing processes
        with open(tasksFile, 'rt') as tasks:
            task = None
            for task in tasks:
                logging.warning('Run has left-over process with pid {0}, sending signal {1} (try {2}).'.format(task, sig, i+1))
                Util.killProcess(int(task), sig)

            if task is None:
                return # No process was hanging, exit
            elif sig == signal.SIGKILL:
                logging.warning('Run still has left over processes after third try of killing them, giving up.')
            
            time.sleep(0.5) # wait for the process to exit, this might take some time
    
    with open(tasksFile, 'rt') as tasks:
        for task in tasks:
            logging.warning('Run has left-over process with pid {0}, we could not kill it.'.format(task))


def removeCgroup(cgroup):
    if cgroup:
        if not os.path.exists(cgroup):
            logging.warning('Cannot remove CGroup {0}, because it does not exist.'.format(cgroup))
            return
        assert os.path.getsize(os.path.join(cgroup, 'tasks')) == 0
        try:
            os.rmdir(cgroup)
        except OSError:
            # sometimes this fails because the cgroup is still busy, we try again once
            try:
                os.rmdir(cgroup)
            except OSError as e:
                logging.warning("Failed to remove cgroup {0}: error {1} ({2})"
                                .format(cgroup, e.errno, e.strerror))
