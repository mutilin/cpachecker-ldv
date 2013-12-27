#!/usr/bin/perl -w

use Getopt::Long qw(GetOptions);
Getopt::Long::Configure qw(posix_default no_ignore_case);
use strict;

my $visualize_fname;
my $cilpath;
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
) or usage("Unrecognized options!");

usage("Can't find CPAChecker trace!") unless $visualize_fname;
open(my $visualize_fh, "<", $visualize_fname) or usage("Can't open file for read");
open(my $html_result, ">", $root_html_file) or usage("Can't open file for write");

usage("Can't find cil-file") unless $cilpath;

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
				$current_fname = "pointer/$current_varname".$pointer;
			} else {
				$current_fname = "struct/$current_varname";
			}
		} elsif ($line =~ /^##(.+)/) {
			my $funcName = $1;
			if ( $pointer > 0) {
				$current_fname = "pointer/$current_varname".$pointer."_".$funcName;
			} else {
				$current_fname = "local/$current_varname"."_".$funcName;
			}
		} elsif ($line =~ /^#/) {
			if ( $pointer > 0) {
				$current_fname = "pointer/$current_varname".$pointer;
			} else {
				$current_fname = "global/$current_varname";
			}
		}
		push(@{$unsafe_list{$current_fname}}, $current_varname_title);
		open($currentUnsafe, ">", $current_fname) or die("Can't open file for write unsafe");		
	} else {
		print($currentUnsafe $line);
	}
}

# Create only list of unsafes
foreach my $current_fname(sort keys %unsafe_list)
{
	print ($html_result "<li><a href = \"$current_fname.html\">$current_fname</a></li>");
}

print "General statistics is generated\n";

# Now create all html-pages with unsafes

foreach my $current_fname(sort keys %unsafe_list)
{
	`etv -c $current_fname -i $cilpath --format "CPAchecker error trace v1.1" -r reqs`;
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
	`etv -c $current_fname -i $cilpath --format "CPAchecker error trace v1.1" -s srcs -o $current_fname.tmp`;
	die ("etv failed") if( $? == -1 ) ;
	open(my $html_tmp, ">", "$current_fname.html") or die("Can't open html-file for write");
	print($html_tmp "<html> <body> <div id='SSHeader'><div id='SSHeaderLogo'>@{$unsafe_list{$current_fname}}</div></div>");
	`cat $current_fname.tmp >> $current_fname.html && echo "</body></html>" >> $current_fname.html`;
	die ("Can't cat $current_fname.html") if ($? == -1);
	unlink "$current_fname.tmp" or die;
	print "Generate ".$current_fname."\n";
}


