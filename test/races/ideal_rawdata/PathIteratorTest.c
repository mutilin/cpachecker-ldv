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
none:	N29 -{INIT GLOBAL VARS}-> N32
none:	N40 -{Function start dummy edge}-> N30
line 40:	N30 -{g()}-> N19
none:	N19 -{Function start dummy edge}-> N20
line 30:	N20 -{int p = 0;}-> N21
line 31:	N21 -{f(p)}-> N11
none:	N11 -{Function start dummy edge}-> N12
line 21:	N12 -{h(a)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 7:	N2 -{[a == 0]}-> N3
none:	N3 -{default return}-> N0
line 21:	N0 -{Return edge from h to f}-> N13
none:	N13 -{default return}-> N10
line 31:	N10 -{Return edge from f to g}-> N22
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 32:	N22 -{intLock();}-> N23
line 33:	N23 -{global = 2;}-> N24
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 34:	N24 -{intUnlock();}-> N25
line 35:	N25 -{l(p)}-> N7
none:	N7 -{Function start dummy edge}-> N8
line 15:	N8 -{h(1)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 7:	N2 -{[!(a == 0)]}-> N4
Line 0:     N0 -{highlight}-> N0
line 8:	N4 -{global = 1;}-> N5
none:	N5 -{}-> N3
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*intLock[1]*/}-> N0
none:	N29 -{INIT GLOBAL VARS}-> N32
none:	N40 -{Function start dummy edge}-> N30
line 40:	N30 -{g()}-> N19
none:	N19 -{Function start dummy edge}-> N20
line 30:	N20 -{int p = 0;}-> N21
line 31:	N21 -{f(p)}-> N11
none:	N11 -{Function start dummy edge}-> N12
line 21:	N12 -{h(a)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 7:	N2 -{[a == 0]}-> N3
none:	N3 -{default return}-> N0
line 21:	N0 -{Return edge from h to f}-> N13
none:	N13 -{default return}-> N10
line 31:	N10 -{Return edge from f to g}-> N22
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 32:	N22 -{intLock();}-> N23
Line 0:     N0 -{highlight}-> N0
line 33:	N23 -{global = 2;}-> N24
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 34:	N24 -{intUnlock();}-> N25
line 35:	N25 -{l(p)}-> N7
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

