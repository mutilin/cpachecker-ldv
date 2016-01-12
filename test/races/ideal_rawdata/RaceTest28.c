2
0
0
0
0
0
2
#
0
int test_unsafe
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N23 -{INIT GLOBAL VARS}-> N26
none:	N32 -{Function start dummy edge}-> N24
line 29:	N24 -{main()}-> N15
none:	N15 -{Function start dummy edge}-> N16
line 21:	N16 -{true_unsafe = 1;}-> N17
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 22:	N17 -{intLock();}-> N18
line 23:	N18 -{test_unsafe = 2;}-> N19
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 24:	N19 -{intUnlock();}-> N20
line 25:	N20 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 7:	N2 -{int b = global;}-> N3
line 8:	N3 -{[!(b == 0)]}-> N4
line 11:	N4 -{b = global;}-> N7
line 12:	N7 -{[b == 0]}-> N9
Line 0:     N0 -{highlight}-> N0
line 13:	N9 -{test_unsafe = 2;}-> N10
none:	N10 -{}-> N8
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*intLock[1]*/}-> N0
none:	N23 -{INIT GLOBAL VARS}-> N26
none:	N32 -{Function start dummy edge}-> N24
line 29:	N24 -{main()}-> N15
none:	N15 -{Function start dummy edge}-> N16
line 21:	N16 -{true_unsafe = 1;}-> N17
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 22:	N17 -{intLock();}-> N18
Line 0:     N0 -{highlight}-> N0
line 23:	N18 -{test_unsafe = 2;}-> N19
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 24:	N19 -{intUnlock();}-> N20
line 25:	N20 -{f()}-> N1
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

#
0
int true_unsafe
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:1*/}-> N0
Line 0:     N0 -{/*Number of usages      :1*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N23 -{INIT GLOBAL VARS}-> N26
none:	N32 -{Function start dummy edge}-> N24
line 29:	N24 -{main()}-> N15
none:	N15 -{Function start dummy edge}-> N16
Line 0:     N0 -{highlight}-> N0
line 21:	N16 -{true_unsafe = 1;}-> N17
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 22:	N17 -{intLock();}-> N18
line 23:	N18 -{test_unsafe = 2;}-> N19
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 24:	N19 -{intUnlock();}-> N20
line 25:	N20 -{f()}-> N1
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N23 -{INIT GLOBAL VARS}-> N26
none:	N32 -{Function start dummy edge}-> N24
line 29:	N24 -{main()}-> N15
none:	N15 -{Function start dummy edge}-> N16
Line 0:     N0 -{highlight}-> N0
line 21:	N16 -{true_unsafe = 1;}-> N17
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 22:	N17 -{intLock();}-> N18
line 23:	N18 -{test_unsafe = 2;}-> N19
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 24:	N19 -{intUnlock();}-> N20
line 25:	N20 -{f()}-> N1
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

