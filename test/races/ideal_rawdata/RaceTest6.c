1
0
0
0
0
0
1
#
0
int global
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*example_lock[1]*/}-> N0
none:	N17 -{INIT GLOBAL VARS}-> N32
none:	N36 -{Function start dummy edge}-> N18
line 19:	N19 -{__CPAchecker_TMP_0 = undef_int();}-> N20
lines 19-27:	N20 -{switch (__CPAchecker_TMP_0)}-> N21
line 25:	N28 -{increase()}-> N9
none:	N9 -{Function start dummy edge}-> N10
Line 0:     N0 -{/*Change states for locks example_lock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 13:	N10 -{lock();}-> N11
Line 0:     N0 -{highlight}-> N0
line 14:	N11 -{int __CPAchecker_TMP_0 = global;}-> N12
Line 0:     N0 -{highlight}-> N0
line 14:	N12 -{global = global + 1;}-> N13
line 14:	N13 -{__CPAchecker_TMP_0;}-> N14
Line 0:     N0 -{/*Change states for locks example_lock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 15:	N14 -{unlock();}-> N15
none:	N15 -{default return}-> N8
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N17 -{INIT GLOBAL VARS}-> N32
none:	N36 -{Function start dummy edge}-> N18
line 19:	N19 -{__CPAchecker_TMP_0 = undef_int();}-> N20
lines 19-27:	N20 -{switch (__CPAchecker_TMP_0)}-> N21
line 21:	N24 -{print()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{highlight}-> N0
line 5:	N2 -{[!((global % 2) == 0)]}-> N5
line 8:	N5 -{printf("global is odd: %d", global);}-> N7
none:	N7 -{}-> N3
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

