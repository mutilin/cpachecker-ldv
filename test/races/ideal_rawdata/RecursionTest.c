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
none:	N14 -{INIT GLOBAL VARS}-> N17
none:	N22 -{Function start dummy edge}-> N15
line 20:	N15 -{a()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 6:	N2 -{int c;}-> N3
line 7:	N3 -{[!(c == 0)]}-> N5
line 8:	N5 -{b()}-> N8
none:	N8 -{Function start dummy edge}-> N9
line 13:	N9 -{int c;}-> N10
line 14:	N10 -{a();}-> N11
Line 0:     N0 -{highlight}-> N0
line 16:	N11 -{global = 0;}-> N12
none:	N12 -{default return}-> N7
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N14 -{INIT GLOBAL VARS}-> N17
none:	N22 -{Function start dummy edge}-> N15
line 20:	N15 -{a()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 6:	N2 -{int c;}-> N3
line 7:	N3 -{[!(c == 0)]}-> N5
line 8:	N5 -{b()}-> N8
none:	N8 -{Function start dummy edge}-> N9
line 13:	N9 -{int c;}-> N10
line 14:	N10 -{a();}-> N11
Line 0:     N0 -{highlight}-> N0
line 16:	N11 -{global = 0;}-> N12
none:	N12 -{default return}-> N7
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

