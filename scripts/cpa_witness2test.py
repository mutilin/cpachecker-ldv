#!/usr/bin/env python3

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

import sys
import os
import glob
import subprocess
import argparse

import logging
import re

"""
CPA-witness2test module for validating witness files by using a generate and validate approach.
Creates a test harness based on the violation witness given for an input file,
compiles the file with the created harness and checks whether the created program
reaches the target location specified by the violation witness.

Currently, only reachability properties are supported.
"""

__version__ = '0.1'


GCC_ARGS_FIXED = ["-D__alias__(x)="]
EXPECTED_RETURN = 107

MACHINE_MODEL_32 = '32bit'
MACHINE_MODEL_64 = '64bit'

HARNESS_EXE_NAME = 'test_suite'

RESULT_ACCEPT = 'accept'
RESULT_REJECT = 'reject'
RESULT_UNK = 'unknown'

ARG_PLACEHOLDER = '9949e80c0b01459493a90a2a5646ffbc'  # Random string generated by uuid.uuid4()


class ValidationError(Exception):
    """Exception representing an validation error."""

    def __init__(self, msg):
        self._msg = msg

    @property
    def msg(self):
        return self._msg


class ExecutionResult(object):
    """Results of a subprocess execution."""

    def __init__(self, returncode, stdout, stderr):
        self._returncode = returncode
        self._stdout = stdout
        self._stderr = stderr

    @property
    def returncode(self):
        return self._returncode

    @property
    def stdout(self):
        return self._stdout

    @property
    def stderr(self):
        return self._stderr


def get_cpachecker_version():
    executable = get_cpachecker_executable()
    result = execute([executable, "-help"], quiet=True)
    for line in result.stdout.split(os.linesep):
        if line.startswith('CPAchecker'):
            return line.replace('CPAchecker', '').strip()
    return None


def create_parser():
    descr="Validate a given violation witness for an input file."
    if sys.version_info >= (3,5):
        parser = argparse.ArgumentParser(description=descr, add_help=False, allow_abbrev=False)
    else:
        parser = argparse.ArgumentParser(description=descr, add_help=False)

    parser.add_argument("-help",
                        action='help'
                        )

    parser.add_argument("-version",
                        action="version", version='{}'.format(get_cpachecker_version())
                        )

    machine_model_args = parser.add_mutually_exclusive_group(required=False)
    machine_model_args.add_argument('-32',
                                    dest='machine_model', action='store_const', const=MACHINE_MODEL_32,
                                    help="use 32 bit machine model"
                                    )
    machine_model_args.add_argument('-64',
                                    dest='machine_model', action='store_const', const=MACHINE_MODEL_64,
                                    help="use 64 bit machine model"
                                    )
    machine_model_args.set_defaults(machine_model=MACHINE_MODEL_32)

    parser.add_argument('-outputpath',
                        dest='output_path',
                        type=str, action='store', default="output",
                        help="path where output should be stored"
                        )

    parser.add_argument('-stats',
                        action='store_true',
                        help="show statistics")

    parser.add_argument('-gcc-args',
                        dest='gcc_args',
                        type=str,
                        action='store',
                        nargs=argparse.REMAINDER,
                        default=[],
                        help='list of arguments to use when compiling the counterexample test'
                        )

    parser.add_argument("file",
                        type=str,
                        nargs='?',
                        help="file to validate witness for"
                        )

    return parser


def _parse_args(argv=sys.argv[1:]):
    parser = create_parser()
    args = parser.parse_known_args(argv[:-1])[0]
    args_file = parser.parse_args([argv[-1]])  # Parse the file name
    args.file = args_file.file

    return args


def flatten(list_of_lists):
    return sum(list_of_lists, [])


def _create_gcc_basic_args(args):
    gcc_args = GCC_ARGS_FIXED + args.gcc_args
    if args.machine_model == MACHINE_MODEL_64:
        gcc_args.append('-m64')
    elif args.machine_model == MACHINE_MODEL_32:
        gcc_args.append('-m32')
    else:
        raise ValidationError('Neither 32 nor 64 bit machine model specified')

    return gcc_args


def _create_gcc_cmd_tail(harness, file, target):
    return ['-o', target, harness, file]


def create_compile_cmd(harness, target, args, c_version='c11'):
    gcc_cmd = ['gcc'] + _create_gcc_basic_args(args)
    gcc_cmd.append('-std={}'.format(c_version))
    gcc_cmd += _create_gcc_cmd_tail(harness, args.file, target)
    return gcc_cmd


def _create_cpachecker_args(args):
    cpachecker_args = sys.argv[1:]

    for gcc_arg in ['-gcc-args'] + args.gcc_args:
        if gcc_arg in cpachecker_args:
            cpachecker_args.remove(gcc_arg)

    return cpachecker_args


def get_cpachecker_executable():
    executable_name = 'cpa.sh'

    def is_exe(exe_path):
        return os.path.isfile(exe_path) and os.access(exe_path, os.X_OK)

    # Directories the CPAchecker executable may ly in.
    # It's important to put '.' and './scripts' last, because we
    # want to look at the "real" PATH directories first
    path_candidates = os.environ["PATH"].split(os.pathsep) + ['.', '.' + os.sep + 'scripts']
    for path in path_candidates:
        path = path.strip('"')
        exe_file = os.path.join(path, executable_name)
        if is_exe(exe_file):
            return exe_file

    raise ValidationError("CPAchecker executable not found or not executable!")


def create_harness_gen_cmd(args):
    cpa_executable = get_cpachecker_executable()
    harness_gen_args = _create_cpachecker_args(args)
    return [cpa_executable] + harness_gen_args


def find_harnesses(output_path):
    return glob.glob(output_path + "/*harness.c")


def get_target_name(harness_name):
    harness_number = re.search(r'(\d+)\.harness\.c', harness_name).group(1)

    return "test_cex" + harness_number


def execute(command, quiet=False):
    if not quiet:
        logging.info(" ".join(command))
    p = subprocess.Popen(command,
                         stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE,
                         universal_newlines=True
                         )
    returncode = p.wait()
    output = p.stdout.read()
    err_output = p.stderr.read()
    return ExecutionResult(returncode, output, err_output)


def analyze_result(test_result, harness):
    if test_result.returncode == EXPECTED_RETURN:
        logging.info("Harness {} reached expected error location.".format(harness))
        return RESULT_ACCEPT
    elif test_result.returncode == 0:
        logging.info("Harness {} did not encounter _any_ error".format(harness))
        return RESULT_REJECT
    else:
        logging.info("Run with harness {} was not successful".format(harness))
        return RESULT_UNK


def log_multiline(msg, level=logging.INFO):
    if type(msg) is list:
        msg_lines = msg
    else:
        msg_lines = msg.split('\n')
    for line in msg_lines:
        logging.log(level, line)


def run():
    statistics = []
    args = _parse_args()
    output_dir = args.output_path

    harness_gen_cmd = create_harness_gen_cmd(args)
    harness_gen_result = execute(harness_gen_cmd)
    log_multiline(harness_gen_result.stderr, level=logging.INFO)
    log_multiline(harness_gen_result.stdout, level=logging.DEBUG)

    created_harnesses = find_harnesses(output_dir)
    statistics.append(("Harnesses produced", len(created_harnesses)))

    final_result = None
    successful_harness = None
    iter_count = 0  # Count how many harnesses were tested
    compile_success_count = 0  # Count how often compilation overall was successful
    c11_success_count = 0  # Count how often compilation with C11 standard was sucessful
    reject_count = 0
    for harness in created_harnesses:
        iter_count += 1
        logging.info("Looking at {}".format(harness))
        exe_target = output_dir + os.sep + get_target_name(harness)
        compile_cmd = create_compile_cmd(harness, exe_target, args)
        compile_result = execute(compile_cmd)

        log_multiline(compile_result.stderr, level=logging.INFO)
        log_multiline(compile_result.stdout, level=logging.DEBUG)

        if compile_result.returncode != 0:
            compile_cmd = create_compile_cmd(harness, exe_target, args, 'c90')
            compile_result = execute(compile_cmd)
            log_multiline(compile_result.stderr, level=logging.INFO)
            log_multiline(compile_result.stdout, level=logging.DEBUG)

            if compile_result.returncode != 0:
                logging.warning("Compilation failed for harness {}".format(harness))
                continue

        else:
            c11_success_count += 1
        compile_success_count += 1

        test_result = execute([exe_target])
        test_stdout_file = output_dir + os.sep + 'stdout.txt'
        test_stderr_file = output_dir + os.sep + 'stderr.txt'
        with open(test_stdout_file, 'w+') as output:
            output.write(test_result.stdout)
            logging.info("Wrote stdout of test execution to {}".format(test_stdout_file))
        with open(test_stderr_file, 'w+') as error_output:
            error_output.write(test_result.stderr)
            logging.info("Wrote stderr of test execution to {}".format(test_stderr_file))

        result = analyze_result(test_result, harness)
        if result == RESULT_ACCEPT:
            successful_harness = harness
            final_result = RESULT_ACCEPT
            break
        elif result == RESULT_REJECT:
            reject_count += 1
            if not final_result:
                final_result = RESULT_REJECT  # Only set final result to 'reject' if no harness produces any error
        else:
            final_result = RESULT_UNK

    if compile_success_count == 0:
        raise ValidationError("Compilation failed for every harness/file pair.")

    statistics.append(("Harnesses tested", iter_count))
    statistics.append(("C11 compatible", c11_success_count))
    statistics.append(("Harnesses rejected", reject_count))

    if args.stats:
        print(os.linesep + "Statistics:")
        for prop, value in statistics:
            print("\t" + str(prop) + ": " + str(value))
        print()

    if final_result == RESULT_ACCEPT:
        print("Verification result: FALSE. Harness {} was successful.".format(successful_harness))
    elif final_result == RESULT_REJECT:
        print("Verification result: TRUE. No harness produced any error.")
    else:
        print("Verification result: UNKNOWN." +
              " No harness for witness was successful or no harness was produced.")


logging.basicConfig(format="%(levelname)s: %(message)s",
                    level=logging.INFO)

try:
    run()
except ValidationError as e:
    logging.error(e.msg)
    print("Verification result: ERROR.")
