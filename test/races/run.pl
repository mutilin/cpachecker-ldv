#!/usr/bin/perl -w

use Getopt::Long qw(GetOptions);
Getopt::Long::Configure qw(posix_default no_ignore_case);
use strict;


my $prefix="/home/alpha/git/cpachecker";
my $passed_tests=0;
my $failed_tests=0;
my @failed_test_names;
my $tool_failures=0;
my @tool_failure_names;
my $new_tests = 0;
my @new_test_names;

system("rm -rf output");
system("mkdir output");
foreach my $test_file (glob($prefix."/test/races/src/*.c"))
{
#my $test_file = $prefix."/test/races/src/RaceTest5.c";
    my $ideal_path = $test_file;
	$ideal_path =~ m|.*\/(\w+).c|;
    my $name = $1;
    print "INFO: start test $name\n";
    #print "$prefix/scripts/cpa.sh  -config $prefix/test/config/lockstat/local-lock-combination.properties -entryfunction ldv_main $test_file -heap 10g 2>&1 -setprop \"cegar.refinementLoops = 10\" > ./output/log_$name\n";
	system("$prefix/scripts/cpa.sh  -config $prefix/test/config/lockstat/local-lock-combination.properties -entryfunction ldv_main $test_file -heap 10g -setprop \"cegar.refinementLoops = 10\" > ./output/log_$name 2>&1");
	$ideal_path =~ s|src|ideal_rawdata|g;
    if ( -e "./output/unsafe_rawdata") {
        if (-e $ideal_path) {
            my $result = system("cmp ./output/unsafe_rawdata $ideal_path");
            #print "$i\n";
            if ($result == 0) {
                #Successful finish
                $passed_tests++;
                print "INFO: Test is successfully finished\n";
            } else {
                $failed_tests++;
                push(@failed_test_names, $name);
                print "WARNING: Test $name FAILED!\n";
                system("cp output/unsafe_rawdata output/unsafe_rawdata_$name.new");
            }
        } else {
            $new_tests++;
            push(@new_test_names, $name);
            print "WARNING: $name doesn't have an ideal result to compare\n";
            system("cp output/unsafe_rawdata output/unsafe_rawdata_$name.new");
        }
        #Next test can fail and use this file for comparison
        system("rm output/unsafe_rawdata");
    } else {
        $tool_failures++;
        push(@tool_failure_names, $name);
        print "WARNING: Tool failed on test $name!\n";
    }
}
print "INFO: Passed tests: $passed_tests\n";
if ( $failed_tests > 0) {
    print "INFO: Failed tests: $failed_tests\n";
    print "@failed_test_names\n";
}
if ($new_tests > 0) {
    print "INFO: New tests: $new_tests\n";
    print "@new_test_names\n";
}
if ($tool_failures > 0) {
    print "INFO: Tool failures: $tool_failures\n";
    print "@tool_failure_names\n";
}
