#!/usr/bin/perl -w

# CPAchecker is a tool for configurable software verification.
# This file is part of CPAchecker.

# Copyright (C) 2007-2012  Dirk Beyer
# All rights reserved.

# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at

#    http://www.apache.org/licenses/LICENSE-2.0

# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


# CPAchecker web page:
#  http://cpachecker.sosy-lab.org



# This script replaces lines from cil-file to origine lines in coverage report.
# It requires .info file, as input. 
# You may also specify output directory and original cil-file.

# Input for this script (.info file) is generated by CoverCPA and is located in output/ directory.
# So, to get report you should add this CPA to your configuration, run CPAchecker, then run this script.

use File::Basename;
use Getopt::Long qw(GetOptions);
Getopt::Long::Configure qw(posix_default no_ignore_case);
use strict;

`which genhtml` or die "genhtml (from lcov package) not found";

sub usage{ 
        my $msg=shift;
print STDERR $msg."\n";

print STDERR<<usage_ends;

Script, which generates HTML coverage report for INFOFILE using genhtml 

Usage: 
	gen-coverage-report.pl [OPTIONS] --info=INFOFILE

Required options:
--i, --info=INFOFILE       	  Coverage report generated by CPAchecker in LCOV format

Additional options:
--o, --output-directory=OUTDIR     Write HTML output to OUTDIR
--cil=CILFILE                      Path to original CIL file (if you want to override path saved in INFOFILE)
--skip=SKIPEXPRESSION              Functions, satisfying this regular expression, will be skipped

Example: gen-coverage-report.pl --i=cil.out.i.info --o=./tmpdir --s=^func_(\\d+)\$

usage_ends
        die;
}

my $output_dir = ".";
my $lcov_info_fname;
my $cil_path_override;
my $skip_expression;

GetOptions(
        'info|i=s'=>\$lcov_info_fname,
        'output-directory|o=s'=>\$output_dir,
        'cil|c=s'=>\$cil_path_override,
        'skip|s=s'=>\$skip_expression,
) or usage("Unrecognized options!");

usage("LCOV info file isn't specified") unless $lcov_info_fname;
open(my $lcov_info_fh, "<", $lcov_info_fname) or usage("Can't open LCOV info file for read");

print "Using output directory $output_dir\n";

my $fname = basename($lcov_info_fname);
my $info_dir = dirname($lcov_info_fname);
my $output_lcov = $output_dir.$fname.".orig";

open(my $new_lcov_info_fh, ">", $output_lcov) or usage("Can't open LCOV file for write: $output_lcov");


# We'd like to relate a given CPAchecker error trace with original source code,
# not with the CIL one. So first of all build a map between sources.
# Read a CIL source file from the beginning of the error trace.
my %src_map;

sub process_cil_file ($)
{
  my $cil_fname = shift;
  if(defined($cil_path_override)) {
	$cil_fname = $cil_path_override;
	print "Overriding path to cil by $cil_path_override\n";
  }
  open(my $cil_fh, "<", $cil_fname) or die("Can't open CIL file for read $cil_fname\n"."You may specify it using --cil option");

  my $src_cur = '';
  my $src_line_cur = 0;
  my $cil_line_cur = 1;

  while (<$cil_fh>)
  {
    my $str = $_;

    chomp($str);

    if ($str =~ /^#line (\d+) "([^"]+)"$/)
    {
      $src_line_cur = $1;
      $src_cur = $2;
    }
    elsif ($str =~ /^#line (\d+)$/)
    {
      $src_line_cur = $1;
    }
    else
    {
      $src_map{$cil_line_cur} = {
          'file' => $src_cur
        , 'line' => $src_line_cur};
    }

    $cil_line_cur++;
  }
}

my %info_fn;
my %info_fnda;
my %info_da;
my @skipped_lines;
my $text;

top:while (<$lcov_info_fh>)
{
  my $str = $_;
  
  chomp($str);
  
  if ($str =~ /^TN:(.*)/) {
    $text = $1;
  }
  
  if ($str =~ /^SF:(.+)$/)
  {
    process_cil_file ($1);
  }
  
  if ($str =~ /^FN:(\d+),(.+)$/)
  {
    my $start_location = $1;
    my $orig_location = $src_map{$start_location} or die("Can't get original location for line '$1'");
    my $start_line = $orig_location->{'line'};
    my $func_name = $2;
    $str = <$lcov_info_fh>;
    chomp($str);
    $str =~ /^#FN:(\d+)$/;
    my $end_location = $1;
    if (defined($skip_expression) && $func_name =~ m/$skip_expression/) {
      push(@skipped_lines, {'start'=>$start_location, 'end'=>$end_location});
    } else {
      push(@{$info_fn{$orig_location->{'file'}}}, {'line' => $start_line, 'func'=>$func_name});
    }
  }

  if ($str =~ /^FNDA:(\d+),(.+)$/)
  {
    $info_fnda{$2} = $1;
  }
  
  if ($str =~ /^DA:(\d+),(.+)$/)
  {
    my $location = $1;
    my $orig_location = $src_map{$location} or die("Can't get original location for line '$1'");
		 
    foreach my $skip (@skipped_lines)
    {
      #this line should be deleted from report. Skip it.
      next top if ($skip->{'start'} < $location && $skip->{'end'} > $location)
    }			
     
    foreach my $info (@{$info_da{$orig_location->{'file'}}})
    {
      next top if ($info->{'line'} == $orig_location->{'line'} && $info->{'used'} == $2) 
    }

    push(@{$info_da{$orig_location->{'file'}}}, {'line' => $orig_location->{'line'}, 'used'=>$2});
  }
}

foreach my $file (keys(%info_fn))
{
  if ($file !~ '^/')
  {
    print("File '$file' has relative path and was skipped\n");
    next;
  }
  
  if (!-f $file)
  {
    print("Skipped file '$file'\n");
    next;
  }

  print ($new_lcov_info_fh "TN:$text\nSF:$file\n");
  
  my $info_fn_for_file = $info_fn{$file};
  my @fn_names;
  foreach my $info_fn (@{$info_fn_for_file})
  {
    my $fn_name = $info_fn->{'func'};
    push(@fn_names, $fn_name);
    my $fn_line = $info_fn->{'line'};
    
    print ($new_lcov_info_fh "FN:$fn_line,$fn_name\n");
  }
  
  foreach my $fn_name (@fn_names)
  {
    if ($info_fnda{$fn_name})
    {
      print ($new_lcov_info_fh "FNDA:$info_fnda{$fn_name},$fn_name\n");
    }
  }
  
  my $info_da_for_file = $info_da{$file};
  # We should remember, which lines we've printed,
  # because there may be several lines transfered into one original line
  my %existed_lines;
  foreach my $info_da (@{$info_da_for_file})
  {
    my $used = $info_da->{'used'};
    my $line = $info_da->{'line'};
    
    # +1 is used because 0 is returned if there aren't element
    if (exists($existed_lines{$line}))
    {
      $existed_lines{$line} = $used + $existed_lines{$line};
    } else {
	  $existed_lines{$line} = $used;
	}
  }
  
  foreach my $key (keys %existed_lines)
  {
    print ($new_lcov_info_fh "DA:$key,$existed_lines{$key}\n");
  }
  
  print ($new_lcov_info_fh "end_of_record\n");
}

close($new_lcov_info_fh);
system("genhtml --output-directory $output_dir --legend --quiet $output_lcov");
