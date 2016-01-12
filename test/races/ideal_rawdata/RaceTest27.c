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
Line 0:     N0 -{/*Number of usage points:1*/}-> N0
Line 0:     N0 -{/*Number of usages      :1*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
Line 0:     N0 -{/*Failure in refinement*/}-> N0
none:	N15 -{INIT GLOBAL VARS}-> N18
none:	N21 -{Function start dummy edge}-> N16
line 21:	N16 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 7:	N2 -{int i = 0;}-> N3
lines 8-15:	N3 -{while}-> N4
lines 8-15:	N4 -{}-> N5
line 9:	N5 -{[i < 10]}-> N8
none:	N8 -{}-> N7
line 14:	N7 -{i = i + 1;}-> N11
none:	N11 -{}-> N4
lines 8-15:	N4 -{}-> N5
line 9:	N5 -{[i < 10]}-> N8
none:	N8 -{}-> N7
line 14:	N7 -{i = i + 1;}-> N11
none:	N11 -{}-> N4
lines 8-15:	N4 -{}-> N5
line 9:	N5 -{[!(i < 10)]}-> N9
line 12:	N9 -{break}-> N6
Line 0:     N0 -{highlight}-> N0
line 16:	N6 -{global = 1;}-> N12
line 17:	N12 -{return 0;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
Line 0:     N0 -{/*Failure in refinement*/}-> N0
none:	N15 -{INIT GLOBAL VARS}-> N18
none:	N21 -{Function start dummy edge}-> N16
line 21:	N16 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 7:	N2 -{int i = 0;}-> N3
lines 8-15:	N3 -{while}-> N4
lines 8-15:	N4 -{}-> N5
line 9:	N5 -{[i < 10]}-> N8
none:	N8 -{}-> N7
line 14:	N7 -{i = i + 1;}-> N11
none:	N11 -{}-> N4
lines 8-15:	N4 -{}-> N5
line 9:	N5 -{[i < 10]}-> N8
none:	N8 -{}-> N7
line 14:	N7 -{i = i + 1;}-> N11
none:	N11 -{}-> N4
lines 8-15:	N4 -{}-> N5
line 9:	N5 -{[!(i < 10)]}-> N9
line 12:	N9 -{break}-> N6
Line 0:     N0 -{highlight}-> N0
line 16:	N6 -{global = 1;}-> N12
line 17:	N12 -{return 0;}-> N0
Line 0:     N0 -{return;}-> N0

