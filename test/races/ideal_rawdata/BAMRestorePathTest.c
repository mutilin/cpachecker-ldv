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
none:	N12 -{INIT GLOBAL VARS}-> N18
none:	N22 -{Function start dummy edge}-> N13
line 17:	N14 -{f()}-> N5
none:	N5 -{Function start dummy edge}-> N6
line 8:	N6 -{void *res;}-> N7
line 9:	N7 -{g()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 4:	N2 -{return 0;}-> N0
line 9:	N0 -{Return edge from g to f}-> N8
Line 0:     N0 -{highlight}-> N0
line 10:	N8 -{global = 0;}-> N9
line 11:	N9 -{return res;}-> N4
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N12 -{INIT GLOBAL VARS}-> N18
none:	N22 -{Function start dummy edge}-> N13
line 17:	N14 -{f()}-> N5
none:	N5 -{Function start dummy edge}-> N6
line 8:	N6 -{void *res;}-> N7
line 9:	N7 -{g()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 4:	N2 -{return 0;}-> N0
line 9:	N0 -{Return edge from g to f}-> N8
Line 0:     N0 -{highlight}-> N0
line 10:	N8 -{global = 0;}-> N9
line 11:	N9 -{return res;}-> N4
Line 0:     N0 -{return;}-> N0

