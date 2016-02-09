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
Line 0:     N0 -{/*Failure in refinement*/}-> N0
none:	N19 -{INIT GLOBAL VARS}-> N23
none:	N27 -{Function start dummy edge}-> N20
line 20:	N20 -{global = 1;}-> N21
line 21:	N21 -{main()}-> N5
none:	N5 -{Function start dummy edge}-> N6
line 9:	N6 -{int i = 0;}-> N7
line 10:	N7 -{int res = 0;}-> N8
lines 11-13:	N8 -{for}-> N9
lines 11-13:	N9 -{i = 0;}-> N10
line 11:	N10 -{[i < 10000]}-> N12
line 12:	N12 -{g(res)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 5:	N2 -{return a + 1;}-> N0
line 12:	N0 -{Return edge from g to main}-> N14
none:	N14 -{}-> N11
lines 11-13:	N11 -{i = i + 1;}-> N10
line 11:	N10 -{[i < 10000]}-> N12
line 12:	N12 -{g(res)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 5:	N2 -{return a + 1;}-> N0
line 12:	N0 -{Return edge from g to main}-> N14
none:	N14 -{}-> N11
lines 11-13:	N11 -{i = i + 1;}-> N10
line 11:	N10 -{[!(i < 10000)]}-> N13
line 14:	N13 -{[res < 10000]}-> N16
Line 0:     N0 -{highlight}-> N0
line 15:	N16 -{global = 0;}-> N17
none:	N17 -{}-> N15
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N19 -{INIT GLOBAL VARS}-> N23
none:	N27 -{Function start dummy edge}-> N20
Line 0:     N0 -{highlight}-> N0
line 20:	N20 -{global = 1;}-> N21
line 21:	N21 -{main()}-> N5
Line 0:     N0 -{return;}-> N0

