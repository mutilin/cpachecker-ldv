#!/bin/bash

prefix="/home/alpha/git/cpachecker"
passed_tests=0
failed_tests=0
for test in ./src/*
do
	$prefix/scripts/cpa.sh  -config $prefix/test/config/lockstat/local-lock-combination.properties -entryfunction ldv_main $test -heap 10g 2>&1
	name=`echo $test | sed -e 's|\.\/src\/||g'`
	#i=`diff ./output/unsafe_rawdata ./ideal_rawdata/RaceTest.c`
	#echo $i 
	if cmp output/unsafe_rawdata ./ideal_rawdata/$name >/dev/null ;
	then
		passed_tests=$(($passed_tests+1))
		echo "Test is successfully passed"
	else
		failed_tests=$((failed_tests+1))
		echo "GREAT WARNING! TEST FAILED!"
	fi
done

echo "Passed tests: $passed_tests"
echo "Failed tests: $failed_tests"
