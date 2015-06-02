1
0
0
0
0
0
1
#
0
int unsafe
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N5 -{INIT GLOBAL VARS}-> N9
none:	N13 -{Function start dummy edge}-> N6
Line 0:     N0 -{highlight}-> N0
line 9:	N6 -{unsafe = 0;}-> N7
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 10:	N7 -{intLock()}-> N1
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*intLock[1]*/}-> N0
none:	N5 -{INIT GLOBAL VARS}-> N9
none:	N13 -{Function start dummy edge}-> N6
line 9:	N6 -{unsafe = 0;}-> N7
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 10:	N7 -{intLock()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{highlight}-> N0
line 5:	N2 -{unsafe = 1;}-> N3
none:	N3 -{default return}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

