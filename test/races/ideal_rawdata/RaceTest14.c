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
none:	N23 -{INIT GLOBAL VARS}-> N27
none:	N33 -{Function start dummy edge}-> N24
line 21:	N24 -{main()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{highlight}-> N0
line 5:	N2 -{int __CPAchecker_TMP_0 = global;}-> N3
Line 0:     N0 -{highlight}-> N0
line 5:	N3 -{global = global + 1;}-> N4
line 5:	N4 -{__CPAchecker_TMP_0;}-> N5
none:	N5 -{default return}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*intLock[1], kernDispatchDisable[1]*/}-> N0
none:	N23 -{INIT GLOBAL VARS}-> N27
none:	N33 -{Function start dummy edge}-> N24
line 21:	N24 -{main()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 5:	N2 -{int __CPAchecker_TMP_0 = global;}-> N3
line 5:	N3 -{global = global + 1;}-> N4
line 5:	N4 -{__CPAchecker_TMP_0;}-> N5
none:	N5 -{default return}-> N0
line 21:	N0 -{Return edge from main to ldv_main}-> N25
line 22:	N25 -{f()}-> N7
none:	N7 -{Function start dummy edge}-> N8
line 9:	N8 -{int i = 0;}-> N9
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 10:	N9 -{kernDispatchDisable();}-> N10
line 11:	N10 -{int __CPAchecker_TMP_0 = i;}-> N11
line 11:	N11 -{i = i + 1;}-> N12
line 11:	N12 -{__CPAchecker_TMP_0;}-> N13
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 12:	N13 -{intLock();}-> N14
line 13:	N14 -{g()}-> N17
none:	N17 -{Function start dummy edge}-> N18
Line 0:     N0 -{highlight}-> N0
line 17:	N18 -{int __CPAchecker_TMP_0 = global;}-> N19
Line 0:     N0 -{highlight}-> N0
line 17:	N19 -{global = global + 1;}-> N20
line 17:	N20 -{__CPAchecker_TMP_0;}-> N21
none:	N21 -{default return}-> N16
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

