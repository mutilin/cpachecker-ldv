#!/usr/bin/perl -w

use strict;


my $lcov_info_fname = $ARGV[0] or die("LCOV file isn't specified");
open(my $lcov_info_fh, "<", $lcov_info_fname) or die("Can't open LCOV file for read");
open(my $new_lcov_info_fh, ">", "$lcov_info_fname.orig") or die("Can't open LCOV file for write");

# We'd like to relate a given CPAchecker error trace with original source code,
# not with the CIL one. So first of all build a map between sources.
# Read a CIL source file from the beginning of the error trace.
my %src_map;

sub process_cil_file ($)
{
  my $cil_fname = shift;
  
  open(my $cil_fh, "<", $cil_fname) or die("Can't open CIL file for read");

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
      $src_line_cur++;
    }

    $cil_line_cur++;
  }
}

my %info_fn;
my %info_fnda;
my %info_da;

top:while (<$lcov_info_fh>)
{
  my $str = $_;
  
  chomp($str);
  
  next if ($str =~ /^TN/);
  
  if ($str =~ /^SF:(.+)$/)
  {
    process_cil_file ($1);
  }
  
  if ($str =~ /^FN:(\d+),(.+)$/)
  {
    my $orig_location = $src_map{$1} or die("Can't get original location for line '$1'");
    push(@{$info_fn{$orig_location->{'file'}}}, {'line' => $orig_location->{'line'}, 'func'=>$2});
  }

  if ($str =~ /^FNDA:(\d+),(.+)$/)
  {
    $info_fnda{$2} = $1;
  }
  
  if ($str =~ /^DA:(\d+),(.+)$/)
  {
    my $orig_location = $src_map{$1} or die("Can't get original location for line '$1'");
    
#     if ($orig_location->{'file'} =~ /.+validation.c/  && $orig_location->{'line'} == 224)
#     {
#       print($orig_location->{'file'}."$2\n");
#     }
    
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

  print ($new_lcov_info_fh "TN:\nSF:$file\n");
  
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
  foreach my $info_da (@{$info_da_for_file})
  {
    my $used = $info_da->{'used'};
    my $line = $info_da->{'line'};
    
    print ($new_lcov_info_fh "DA:$line,$used\n");
  }
  
  print ($new_lcov_info_fh "end_of_record\n");
}
