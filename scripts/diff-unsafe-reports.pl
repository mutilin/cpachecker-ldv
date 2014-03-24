#!/usr/bin/perl -w

use XML::Twig;
use strict;
use Getopt::Long qw(GetOptions);
Getopt::Long::Configure qw(posix_default no_ignore_case);


sub usage{
        my $msg=shift;
	print STDERR $msg."\n";

	print STDERR<<usage_ends;

Script, which generates diff between two unsafe reports

Usage: 
        gen-cmdstream.pl --old=old_unsafe_rawdata --new=new_unsafe_rawdata [OPTIONS]
        
Options:
		--output, --o 	- output directory

usage_ends
        die;
}

sub find_var {
	my ($set, $target) = @_;
	
	foreach my $var (@{$set}) {
		if ($var->{'type'} eq $target->{'type'} &&
			$var->{'dereference'} == $target->{'dereference'} &&
			$var->{'name'} eq $target->{'name'}) {
				return 1;
		}	
	}
	return 0;
}

sub scan {
	my $stream = shift;
	my $type;
	my $dereference;
	my $name;
	my @set;

	while(<$stream>) {
		$type = $_;
		if ($type =~ /^#/) {
			$dereference = <$stream>;
        	        $name = <$stream>;
                	chomp($type);
              		chomp($dereference);
                	chomp($name);
			if ($name =~ m/.+ \**\w((\w|_|\[|\])+)$/) {
				push(@set, {'type' => $type, 'dereference' => $dereference, 'name' => $1, 'full_name' => $name});
			} else {
				push(@set, {'type' => $type, 'dereference' => $dereference, 'name' => $name, 'full_name' => $name});
			}
		}
	}
	return \@set;
}


my $output = `pwd`;
my $old_file;
my $new_file;

GetOptions(
        'old=s'=>\$old_file,
        'output|o=s'=>\$output,
        'new=s'=>\$new_file,
        ) or usage("Unrecognized options!");

defined($old_file) && ( -e $old_file) or usage("Old unsafe_rawdata was't found");
defined($new_file) && ( -e $new_file) or usage("New unsafe_rawdata was't found");

my $new_unsafes_output = $output."/new-unsafes";
my $deleted_unsafes_output = $output."/deleted-unsafes";
my $tmp_file = $output."/tmp";

my $OLD;
my $NEW;
open($OLD, "<", $old_file) or die("$!");
open($NEW, "<", $new_file) or die("$!");

my $old_variables;
my $new_variables;


$old_variables = scan($OLD);
$new_variables = scan($NEW);

#Find new unsafes
my @new_unsafes;
my $new_var;
foreach $new_var (@$new_variables) {
	unless (find_var($old_variables, $new_var)) {
		push(@new_unsafes, $new_var);
	} 
}
#Find deleted unsafes
my @deleted_unsafes;
foreach my $old_var (@$old_variables) {
	unless (find_var($new_variables, $old_var)) {
		push(@deleted_unsafes, $old_var);
	}
}
print "New unsafes: ".($#new_unsafes + 1)." from ".($#$new_variables + 1)."\n";
foreach my $var (@new_unsafes) {
	print "  $var->{'full_name'}\n";
}
print "Deleted unsafes: ".($#deleted_unsafes + 1)." from ".($#$old_variables + 1)."\n";
foreach my $var (@deleted_unsafes) {
	print "  $var->{'full_name'}\n";
}
