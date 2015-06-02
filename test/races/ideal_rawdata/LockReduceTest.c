1
0
0
0
0
0
1
#
0
int gvar
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*spl[2]*/}-> N0
none:	N9 -{INIT GLOBAL VARS}-> N20
none:	N24 -{Function start dummy edge}-> N10
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 15:	N10 -{intLock();}-> N11
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 16:	N11 -{intLock();}-> N12
Line 0:     N0 -{/*Change states for locks threadLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 17:	N12 -{threadLock();}-> N13
line 19:	N13 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{highlight}-> N0
line 7:	N2 -{gvar = 0;}-> N3
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 8:	N3 -{intUnlock();}-> N4
Line 0:     N0 -{/*Change states for locks threadLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 9:	N4 -{threadLock();}-> N5
Line 0:     N0 -{/*Change states for locks spl*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 10:	N5 -{splbio();}-> N6
line 11:	N6 -{return 0;}-> N0
line 19:	N0 -{Return edge from f to ldv_main}-> N14
line 22:	N14 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{highlight}-> N0
line 7:	N2 -{gvar = 0;}-> N3
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 8:	N3 -{intUnlock();}-> N4
Line 0:     N0 -{/*Change states for locks threadLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 9:	N4 -{threadLock();}-> N5
Line 0:     N0 -{/*Change states for locks spl*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 10:	N5 -{splbio();}-> N6
line 11:	N6 -{return 0;}-> N0
line 22:	N0 -{Return edge from f to ldv_main}-> N15
Line 0:     N0 -{/*Change states for locks threadLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 24:	N15 -{threadUnlock();}-> N16
Line 0:     N0 -{/*Change states for locks threadLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 25:	N16 -{threadUnlock();}-> N17
Line 0:     N0 -{/*Change states for locks threadLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 26:	N17 -{threadUnlock();}-> N18
line 28:	N18 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{highlight}-> N0
line 7:	N2 -{gvar = 0;}-> N3
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 8:	N3 -{intUnlock();}-> N4
Line 0:     N0 -{/*Change states for locks threadLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 9:	N4 -{threadLock();}-> N5
Line 0:     N0 -{/*Change states for locks spl*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 10:	N5 -{splbio();}-> N6
line 11:	N6 -{return 0;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*intLock[2], threadLock[1]*/}-> N0
none:	N9 -{INIT GLOBAL VARS}-> N20
none:	N24 -{Function start dummy edge}-> N10
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 15:	N10 -{intLock();}-> N11
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 16:	N11 -{intLock();}-> N12
Line 0:     N0 -{/*Change states for locks threadLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 17:	N12 -{threadLock();}-> N13
line 19:	N13 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{highlight}-> N0
line 7:	N2 -{gvar = 0;}-> N3
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 8:	N3 -{intUnlock();}-> N4
Line 0:     N0 -{/*Change states for locks threadLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 9:	N4 -{threadLock();}-> N5
Line 0:     N0 -{/*Change states for locks spl*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 10:	N5 -{splbio();}-> N6
line 11:	N6 -{return 0;}-> N0
Line 0:     N0 -{return;}-> N0

