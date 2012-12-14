#!/usr/bin/perl -w

use Getopt::Long qw(GetOptions);
Getopt::Long::Configure qw(posix_default no_ignore_case);
use strict;

my $visualize_fname;
my $cilpath;
my $path_to_etv;
my $root_html_file = "Unsafes.html";

sub usage{ 
        my $msg=shift;
print STDERR $msg."\n";

print STDERR<<usage_ends;

Script, which generates html-file with the list of unsafe reports

Usage:
        gen-unsafes-report.pl --trace=path-to-unsafes-trace-file --cil=path-to-cil-file --ldvrepo=path-to-git-sources-of-ldv-tools

Example: gen-unsafes-report.pl --trace=../test/results/visualize --cil=../cil.out.i --ldvrepo=/home/alpha/git/ldv-tools/

usage_ends
        die;
}

GetOptions(
        'trace|t=s'=>\$visualize_fname,
        'cil|c=s'=>\$cilpath,
        'ldvrepo|r=s'=>\$path_to_etv,
) or usage("Unrecognized options!");

usage("Can't find CPAChecker trace!") unless $visualize_fname;
open(my $visualize_fh, "<", $visualize_fname) or usage("Can't open file for read");
open(my $html_result, ">", $root_html_file) or usage("Can't open file for write");

usage("Can't find cil-file") unless $cilpath;
usage("No path to etv folder (you may specify path to local ldv-tools repository)") unless $path_to_etv;

my $tmp_trace_name = "tmp_trace";
open(my $tmp_trace, ">", $tmp_trace_name) or die("Can't open file tmp_trace for write");
print($tmp_trace "CPAchecker error trace v1.1\n");
print($tmp_trace "-------");
my $String=`pwd`;
chomp($String);
$String=$String."/$cilpath-------\n";
print($tmp_trace $String);

#implement cat $cilpath >> tmp_trace
open(CILFILE, $cilpath) or die("Can't open cil file");	# Open the cil file
my @cillines = <CILFILE>;		# Read it into an array
close(CILFILE);			# Close the file
print($tmp_trace @cillines);	# Print the array

print($tmp_trace "--------------\n");
close($tmp_trace);

my $line = <$visualize_fh>;
print ($html_result "<b>General statistics</b><p><table><tr><td>");
print ($html_result "Global variables:</td><td>$line</td></tr><tr><td>");
$line = <$visualize_fh>;
print ($html_result "Local variables:</td><td>$line</td></tr><tr><td>");
$line = <$visualize_fh>;
print ($html_result "Structure fields:</td><td>$line</td></tr><tr><td>");
$line = <$visualize_fh>;
print ($html_result "Pointers (any types):</td><td>$line</td></tr>");
$line = <$visualize_fh>;
print ($html_result "<tr><td></td></tr>");
print ($html_result "<tr><td>Total usage:</td><td>$line</td></tr><tr><td>");
$line = <$visualize_fh>;
print ($html_result "Unique usage:</td><td>$line</td></tr><tr><td>");
$line = <$visualize_fh>;
print ($html_result "Skipped cases:</td><td>$line</td></tr><tr><td></table><p>");
my $number = <$visualize_fh>;
print ($html_result "Finded locks:<ol>");
for(my $i = 0; $i < $number; $i++) {
	$line = <$visualize_fh>;
	print ($html_result "<li>$line</li>");
}
print ($html_result "</ol><p>");
$line = <$visualize_fh>;
print ($html_result "<b>Unsafe statistics:</b><br><table><tr><td>Number of unsafes:</td><td>$line</td></tr><tr><td>");
$line = <$visualize_fh>;
print ($html_result "Total usage variables in unsafes:</td><td>$line</td></tr><tr><td>");
$line = <$visualize_fh>;
print ($html_result "Global unsafes:</td><td>$line</td></tr><tr><td>");
$line = <$visualize_fh>;
print ($html_result "Local unsafes:</td><td>$line</td></tr><tr><td>");
$line = <$visualize_fh>;
print ($html_result "Structure fields unsafes:</td><td>$line</td></tr><tr><td>");
$line = <$visualize_fh>;
print ($html_result "Pointers (any types):</td><td>$line</td></tr></table><br>");
print ($html_result "<b>List of unsafes:</b><ol>");
my $currentUnsafe;
my %unsafe_list;

while (<$visualize_fh>) {
	$line = $_;
	
	if ($line =~ /^#(.+)/) {
		my $current_fname;
		my $current_varname;
		if ($line =~ /^###(.+)/) {
			if ( ! -d "struct" ) {
				mkdir("struct") || die "Can't create directory\n";
	    		}
			$current_fname = "struct/$1.tmp";
			$current_varname = <$visualize_fh>;
			push(@{$unsafe_list{$current_fname}}, $current_varname);
			open($currentUnsafe, ">", $current_fname) or die("Can't open file for write unsafe");		
		} elsif ($line =~ /^##(.+)/) {
			if ( ! -d "local" ) {
				mkdir("local") || die "Can't create directory\n";
	    		}
			$current_fname = "local/$1.tmp";
			$current_varname = <$visualize_fh>;
			push(@{$unsafe_list{$current_fname}}, $current_varname);
			open($currentUnsafe, ">", $current_fname) or die("Can't open file for write unsafe");
		} elsif ($line =~ /^#(.+)/) {
			if ( ! -d "global" ) {
				mkdir("global") || die "Can't create directory\n";
	    		}
			$current_fname = "global/$1.tmp";
			$current_varname = <$visualize_fh>;
			push(@{$unsafe_list{$current_fname}}, $current_varname);
			open($currentUnsafe, ">", $current_fname) or die("Can't open file for write unsafe");
		}
	} else {
		print($currentUnsafe $line);
	}
}

my $HEADER = "<html><head><link href='$path_to_etv/stats-visualizer/vhosts/ldv-stats/public/css/etv.css' rel='stylesheet' type='text/css' /><link href='$path_to_etv/stats-visualizer/vhosts/ldv-stats/public/css/etv-analytics-center.css' rel='stylesheet' type='text/css' /><script type='text/javascript' src='$path_to_etv/stats-visualizer/vhosts/ldv-stats/public/jslib/jquery-1.4.2.min.js'></script><script type='text/javascript' src='$path_to_etv/stats-visualizer/vhosts/ldv-stats/public/jslib/etv.js'></script></head>";

my $current_fname_new;
foreach my $current_fname(sort keys %unsafe_list)
{
	$current_fname_new = $current_fname.".new";
	`cat $tmp_trace_name $current_fname > $current_fname_new`;
	die ("cat failed") if( $? == -1 ) ;
	unlink $current_fname or die;
	`etv -c $current_fname_new --reqs-out reqs`;
	die ("etv failed") if( $? == -1 ) ;
	open(my $reqs, ">>", "reqs") or die("Can't open file reqs for write");
	print($reqs "\n");
	open($reqs, "<", "reqs") or die("Can't open file reqs for read");
	unlink "srcs" if -e "srcs";
	open(my $srcs, ">>", "srcs") or die("Can't open file srcs for write");
	while (<$reqs>) {
	  my $nextline = $_;
	  chomp($nextline);
	  if ($nextline) {
	    print($srcs "---LDV---$nextline---LDV---\n");
	    system("cat $nextline >> srcs");
	    die ("Can't cat srcs") if ($? == -1);
	  }
	}
	$current_fname_new =~ m/(.+)\.tmp.new/;
	print ($html_result "<li><a href = \"$1.html\">$1</a></li>");
	`etv -c $current_fname_new --src-files srcs -o $1.html.tmp`;
	die ("etv failed") if( $? == -1 ) ;
	open(my $html_tmp, ">", "$1.html") or die("Can't open html-file for write");
	print($html_tmp "$HEADER <body> <h1>@{$unsafe_list{$current_fname}}</h1>");
	`cat $1.html.tmp >> $1.html && echo "</body></html>" >> $1.html`;
	die ("Can't cat $1.html") if ($? == -1);
	unlink "$1.html.tmp" or die;
	unlink "$1.tmp.new" or die;
	#`rm $1.tmp`;
	print "Generate ".$1."\n";
}


