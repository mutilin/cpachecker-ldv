0
0
0
1
0
0
1
##g
2
int **p
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:1*/}-> N0
Line 0:     N0 -{/*Number of usages      :1*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N12 -{INIT GLOBAL VARS}-> N15
none:	N18 -{Function start dummy edge}-> N13
line 22:	N13 -{g()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 4:	N2 -{int **p;}-> N3
line 5:	N3 -{int *s;}-> N4
line 6:	N4 -{int t;}-> N5
line 8:	N5 -{s = &global;}-> N6
line 9:	N6 -{p = &s;}-> N7
Line 0:     N0 -{highlight}-> N0
line 12:	N7 -{*(*p) = 1;}-> N8
line 15:	N8 -{*p = &t;}-> N9
line 18:	N9 -{*(*p) = 1;}-> N10
none:	N10 -{default return}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N12 -{INIT GLOBAL VARS}-> N15
none:	N18 -{Function start dummy edge}-> N13
line 22:	N13 -{g()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 4:	N2 -{int **p;}-> N3
line 5:	N3 -{int *s;}-> N4
line 6:	N4 -{int t;}-> N5
line 8:	N5 -{s = &global;}-> N6
line 9:	N6 -{p = &s;}-> N7
Line 0:     N0 -{highlight}-> N0
line 12:	N7 -{*(*p) = 1;}-> N8
line 15:	N8 -{*p = &t;}-> N9
line 18:	N9 -{*(*p) = 1;}-> N10
none:	N10 -{default return}-> N0
Line 0:     N0 -{return;}-> N0

