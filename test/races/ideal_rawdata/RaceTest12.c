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
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N18 -{INIT GLOBAL VARS}-> N21
none:	N25 -{Function start dummy edge}-> N19
line 17:	N19 -{g()}-> N10
none:	N10 -{Function start dummy edge}-> N11
line 11:	N11 -{int p = 0;}-> N12
line 12:	N12 -{f(p)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 4:	N2 -{[(10 & a) == 0]}-> N3
line 7:	N3 -{return a;}-> N0
line 12:	N0 -{Return edge from f to g}-> N13
Line 0:     N0 -{highlight}-> N0
line 13:	N13 -{int __CPAchecker_TMP_0 = global;}-> N14
Line 0:     N0 -{highlight}-> N0
line 13:	N14 -{global = global + 1;}-> N15
line 13:	N15 -{__CPAchecker_TMP_0;}-> N16
none:	N16 -{default return}-> N9
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N18 -{INIT GLOBAL VARS}-> N21
none:	N25 -{Function start dummy edge}-> N19
line 17:	N19 -{g()}-> N10
none:	N10 -{Function start dummy edge}-> N11
line 11:	N11 -{int p = 0;}-> N12
line 12:	N12 -{f(p)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 4:	N2 -{[(10 & a) == 0]}-> N3
line 7:	N3 -{return a;}-> N0
line 12:	N0 -{Return edge from f to g}-> N13
Line 0:     N0 -{highlight}-> N0
line 13:	N13 -{int __CPAchecker_TMP_0 = global;}-> N14
Line 0:     N0 -{highlight}-> N0
line 13:	N14 -{global = global + 1;}-> N15
line 13:	N15 -{__CPAchecker_TMP_0;}-> N16
none:	N16 -{default return}-> N9
Line 0:     N0 -{return;}-> N0

