1
0
0
0
0
0
1
#
0
int unsafe2
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*intLock[1]*/}-> N0
none:	N16 -{INIT GLOBAL VARS}-> N20
none:	N25 -{Function start dummy edge}-> N17
line 23:	N17 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 8:	N2 -{intLock();}-> N3
line 9:	N3 -{unsafe = 1;}-> N4
Line 0:     N0 -{highlight}-> N0
line 10:	N4 -{unsafe2 = 1;}-> N5
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 11:	N5 -{intUnlock();}-> N6
none:	N6 -{default return}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N16 -{INIT GLOBAL VARS}-> N20
none:	N25 -{Function start dummy edge}-> N17
line 23:	N17 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 8:	N2 -{intLock();}-> N3
line 9:	N3 -{unsafe = 1;}-> N4
line 10:	N4 -{unsafe2 = 1;}-> N5
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 11:	N5 -{intUnlock();}-> N6
none:	N6 -{default return}-> N0
line 23:	N0 -{Return edge from f to ldv_main}-> N18
line 24:	N18 -{g()}-> N8
none:	N8 -{Function start dummy edge}-> N9
line 15:	N9 -{int tmp;}-> N10
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 16:	N10 -{intLock();}-> N11
line 17:	N11 -{tmp = unsafe;}-> N12
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 18:	N12 -{intUnlock();}-> N13
Line 0:     N0 -{highlight}-> N0
line 19:	N13 -{tmp = unsafe2;}-> N14
none:	N14 -{default return}-> N7
Line 0:     N0 -{return;}-> N0

