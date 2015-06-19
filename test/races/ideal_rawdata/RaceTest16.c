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
none:	N24 -{INIT GLOBAL VARS}-> N28
none:	N33 -{Function start dummy edge}-> N25
line 32:	N26 -{f(40)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 8:	N2 -{int oldflags;}-> N3
line 9:	N3 -{int s;}-> N4
line 10:	N4 -{[oldflags > 36]}-> N6
Line 0:     N0 -{highlight}-> N0
line 11:	N6 -{global = 1;}-> N7
line 12:	N7 -{[!(oldflags == 9)]}-> N8
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N24 -{INIT GLOBAL VARS}-> N28
none:	N33 -{Function start dummy edge}-> N25
line 32:	N26 -{f(40)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 8:	N2 -{int oldflags;}-> N3
line 9:	N3 -{int s;}-> N4
line 10:	N4 -{[oldflags > 36]}-> N6
line 11:	N6 -{global = 1;}-> N7
line 12:	N7 -{[!(oldflags == 9)]}-> N8
line 15:	N8 -{[t == 40]}-> N12
line 16:	N12 -{func(t)}-> N16
none:	N16 -{Function start dummy edge}-> N17
line 23:	N17 -{[!(p == 0)]}-> N18
Line 0:     N0 -{highlight}-> N0
line 26:	N18 -{global = 0;}-> N21
line 27:	N21 -{return 0;}-> N15
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

