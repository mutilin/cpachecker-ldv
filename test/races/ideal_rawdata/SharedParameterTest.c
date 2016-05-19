0
0
0
1
0
0
1
##ldv_main
2
int **c
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N6 -{INIT GLOBAL VARS}-> N21
none:	N24 -{Function start dummy edge}-> N7
line 13:	N9 -{c = malloc(4UL);}-> N10
line 14:	N10 -{*c = malloc(4UL);}-> N11
line 15:	N11 -{d = malloc(4UL);}-> N12
line 16:	N12 -{*d = malloc(4UL);}-> N13
line 18:	N13 -{f(c, d)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 6:	N2 -{*a = &global;}-> N3
line 7:	N3 -{return 0;}-> N0
line 18:	N0 -{Return edge from f to ldv_main}-> N14
Line 0:     N0 -{highlight}-> N0
line 20:	N14 -{*(*c) = 1;}-> N15
line 22:	N15 -{*(*d) = 2;}-> N16
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 25:	N16 -{intLock();}-> N17
line 26:	N17 -{*(*c) = 0;}-> N18
line 27:	N18 -{*(*d) = 1;}-> N19
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 28:	N19 -{intUnlock();}-> N20
none:	N20 -{default return}-> N5

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*intLock[1]*/}-> N0
none:	N6 -{INIT GLOBAL VARS}-> N21
none:	N24 -{Function start dummy edge}-> N7
line 13:	N9 -{c = malloc(4UL);}-> N10
line 14:	N10 -{*c = malloc(4UL);}-> N11
line 15:	N11 -{d = malloc(4UL);}-> N12
line 16:	N12 -{*d = malloc(4UL);}-> N13
line 18:	N13 -{f(c, d)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 6:	N2 -{*a = &global;}-> N3
line 7:	N3 -{return 0;}-> N0
line 18:	N0 -{Return edge from f to ldv_main}-> N14
line 20:	N14 -{*(*c) = 1;}-> N15
line 22:	N15 -{*(*d) = 2;}-> N16
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 25:	N16 -{intLock();}-> N17
Line 0:     N0 -{highlight}-> N0
line 26:	N17 -{*(*c) = 0;}-> N18
line 27:	N18 -{*(*d) = 1;}-> N19
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 28:	N19 -{intUnlock();}-> N20
none:	N20 -{default return}-> N5

