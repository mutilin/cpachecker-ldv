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

Example: gen-unsafes-report.pl --trace=../test/results/unsafe_rawdata --cil=../cil.out.i --ldvrepo=/home/osuser/LDV/ldv-tools/

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

my $line;
my $simple_global_var = <$visualize_fh>;
my $pointer_global_var = <$visualize_fh>;
my $simple_local_var = <$visualize_fh>;
my $pointer_local_var = <$visualize_fh>;
my $simple_field_var = <$visualize_fh>;
my $pointer_field_var = <$visualize_fh>;
my $total_var = <$visualize_fh>;
my $simple_global_unsafe = <$visualize_fh>;
my $pointer_global_unsafe = <$visualize_fh>;
my $simple_local_unsafe = <$visualize_fh>;
my $pointer_local_unsafe = <$visualize_fh>;
my $simple_field_unsafe = <$visualize_fh>;
my $pointer_field_unsafe = <$visualize_fh>;
my $total_unsafe = <$visualize_fh>;
my $pointer;
print ($html_result "<table rules=\"all\" width=\"60%\"><tr><td align=\"center\">Statistics</td><td><b>General</b></td><td><b>Unsafe</b></td></tr>");
print ($html_result "<tr><td><b>Global variables:</b></td><td><b>".($simple_global_var + $pointer_global_var)."</b></td><td><b>".($simple_global_unsafe + $pointer_global_unsafe)."</b></td></tr>");
print ($html_result "<tr><td align=\"center\">Simple:</td><td>$simple_global_var</td><td>$simple_global_unsafe</td></tr>");
print ($html_result "<tr><td align=\"center\">Pointer:</td><td>$pointer_global_var</td><td>$pointer_global_unsafe</td></tr>");
print ($html_result "<tr><td><b>Local variables:</td><td><b>".($simple_local_var + $pointer_local_var)."</b></td><td><b>".($simple_local_unsafe + $pointer_local_unsafe)."</b></td></tr>");
print ($html_result "<tr><td align=\"center\">Simple:</td><td>$simple_local_var</td><td>$simple_local_unsafe</td></tr>");
print ($html_result "<tr><td align=\"center\">Pointer:</td><td>$pointer_local_var</td><td>$pointer_local_unsafe</td></tr>");
print ($html_result "<tr><td><b>Structure fields:</td><td><b>".($simple_field_var + $pointer_field_var)."</b></td><td><b>".($simple_field_unsafe + $pointer_field_unsafe)."</b></td></tr>");
print ($html_result "<tr><td align=\"center\">Simple:</td><td>$simple_field_var</td><td>$simple_field_unsafe</td></tr>");
print ($html_result "<tr><td align=\"center\">Pointer:</td><td>$pointer_field_var</td><td>$pointer_field_unsafe</td></tr>");
print ($html_result "<tr><td></td><td></td><td></td></tr>");
print ($html_result "<tr><td><b>Total variables:</b></td><td>$total_var</td><td>$total_unsafe</td></tr>");
print ($html_result "<tr><td></td><td></td><td></td></tr></table><br>");
my $number = <$visualize_fh>;
print ($html_result "Finded locks:<ol>");
for(my $i = 0; $i < $number; $i++) {
	$line = <$visualize_fh>;
	print ($html_result "<li>$line</li>");
}
print ($html_result "</ol><p>");

print ($html_result "<b>List of unsafes:</b><ol>");
my $currentUnsafe;
my %unsafe_list;

if ( ! -d "struct" ) {
	mkdir("struct") || die "Can't create directory\n";
}
if ( ! -d "local" ) {
	mkdir("local") || die "Can't create directory\n";
}
if ( ! -d "global" ) {
	mkdir("global") || die "Can't create directory\n";
}
if ( ! -d "pointer" ) {
	mkdir("pointer") || die "Can't create directory\n";
}
while (<$visualize_fh>) {
	$line = $_;
	
	my $current_fname;
	my $current_varname;
	my $current_varname_title;
	my $pointer;
	if ($line =~ /^#/) {
		$pointer = <$visualize_fh>;
		chomp($pointer);
		$current_varname = <$visualize_fh>;
		chomp($current_varname);
		$current_varname_title = $current_varname;
		$current_varname =~ s/ /\./g;
		$current_varname =~ s/\*//g;
		$current_varname =~ s/\(//g;
		$current_varname =~ s/\)//g;
		$current_varname =~ s/\.\.//g;
		if ($line =~ /^###/) {
			if ( $pointer > 0) {
				$current_fname = "pointer/$current_varname".$pointer.".tmp";
			} else {
				$current_fname = "struct/$current_varname.tmp";
			}
		} elsif ($line =~ /^##(.+)/) {
			my $funcName = $1;
			if ( $pointer > 0) {
				$current_fname = "pointer/$current_varname".$pointer."_".$funcName.".tmp";
			} else {
				$current_fname = "local/$current_varname"."_".$funcName.".tmp";
			}
		} elsif ($line =~ /^#/) {
			if ( $pointer > 0) {
				$current_fname = "pointer/$current_varname".$pointer.".tmp";
			} else {
				$current_fname = "global/$current_varname.tmp";
			}
		}
		push(@{$unsafe_list{$current_fname}}, $current_varname_title);
		open($currentUnsafe, ">", $current_fname) or die("Can't open file for write unsafe");		
	} else {
		print($currentUnsafe $line);
	}
}

my $HEADER = "<html><head><link href='$path_to_etv/stats-visualizer/vhosts/ldv-stats/public/css/global.css' media='screen' rel='stylesheet' type='text/css' /><link href='$path_to_etv/stats-visualizer/vhosts/ldv-stats/public/css/etv.css' rel='stylesheet' type='text/css' /><link href='$path_to_etv/stats-visualizer/vhosts/ldv-stats/public/css/etv-analytics-center.css' rel='stylesheet' type='text/css' /><script type='text/javascript' src='$path_to_etv/stats-visualizer/vhosts/ldv-stats/public/jslib/jquery-1.4.2.min.js'></script><script type='text/javascript' src='$path_to_etv/stats-visualizer/vhosts/ldv-stats/public/jslib/etv.js'></script><script type='text/javascript' src='$path_to_etv/stats-visualizer/vhosts/ldv-stats/public/jslib/etv-analytics-center.js'></script></head>";

my $current_fname_new;
# Create only list of unsafes
foreach my $current_fname(sort keys %unsafe_list)
{
	$current_fname =~ m/(.+)\.tmp/;
	print ($html_result "<li><a href = \"$1.html\">$1</a></li>");
}

print "General statistics is generated\n";

# Now create all html-pages with unsafes

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
	$current_fname_new =~ m/(.+)\.tmp\.new/;
	`etv -c $current_fname_new --src-files srcs -o $1.html.tmp`;
	die ("etv failed") if( $? == -1 ) ;
	open(my $html_tmp, ">", "$1.html") or die("Can't open html-file for write");
	print($html_tmp "$HEADER <body> <div id='SSHeader'><div id='SSHeaderLogo'>@{$unsafe_list{$current_fname}}</div></div>");
	`cat $1.html.tmp >> $1.html && echo "</body></html>" >> $1.html`;
	die ("Can't cat $1.html") if ($? == -1);
	unlink "$1.html.tmp" or die;
	unlink "$1.tmp.new" or die;
	print "Generate ".$1."\n";
}


