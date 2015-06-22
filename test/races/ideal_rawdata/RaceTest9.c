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
none:	N32 -{INIT GLOBAL VARS}-> N42
none:	N49 -{Function start dummy edge}-> N33
Line 0:     N0 -{highlight}-> N0
line 33:	N34 -{global = global + 1;}-> N35
line 33:	N35 -{__CPAchecker_TMP_0;}-> N36
line 34:	N36 -{m()}-> N13
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*intLock[1]*/}-> N0
none:	N32 -{INIT GLOBAL VARS}-> N42
none:	N49 -{Function start dummy edge}-> N33
line 33:	N34 -{global = global + 1;}-> N35
line 33:	N35 -{__CPAchecker_TMP_0;}-> N36
line 34:	N36 -{m()}-> N13
none:	N13 -{Function start dummy edge}-> N14
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 15:	N14 -{kernDispatchDisable();}-> N15
line 16:	N15 -{g()}-> N9
none:	N9 -{Function start dummy edge}-> N10
line 11:	N10 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 5:	N2 -{int local = 0;}-> N3
line 6:	N3 -{int __CPAchecker_TMP_0 = local;}-> N4
line 6:	N4 -{local = local + 1;}-> N5
line 6:	N5 -{__CPAchecker_TMP_0;}-> N6
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 7:	N6 -{intLock();}-> N7
none:	N7 -{default return}-> N0
line 11:	N0 -{Return edge from f to g}-> N11
none:	N11 -{default return}-> N8
line 16:	N8 -{Return edge from g to m}-> N16
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 17:	N16 -{kernDispatchEnable();}-> N17
none:	N17 -{default return}-> N12
line 34:	N12 -{Return edge from m to ldv_main}-> N37
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 35:	N37 -{intUnlock();}-> N38
line 36:	N38 -{h()}-> N19
none:	N19 -{Function start dummy edge}-> N20
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 21:	N20 -{kernDispatchDisable();}-> N21
line 22:	N21 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 5:	N2 -{int local = 0;}-> N3
line 6:	N3 -{int __CPAchecker_TMP_0 = local;}-> N4
line 6:	N4 -{local = local + 1;}-> N5
line 6:	N5 -{__CPAchecker_TMP_0;}-> N6
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 7:	N6 -{intLock();}-> N7
none:	N7 -{default return}-> N0
line 22:	N0 -{Return edge from f to h}-> N22
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 24:	N22 -{kernDispatchEnable();}-> N23
none:	N23 -{default return}-> N18
line 36:	N18 -{Return edge from h to ldv_main}-> N39
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 37:	N39 -{intUnlock();}-> N40
line 38:	N40 -{h1()}-> N25
none:	N25 -{Function start dummy edge}-> N26
line 28:	N26 -{h()}-> N19
none:	N19 -{Function start dummy edge}-> N20
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 21:	N20 -{kernDispatchDisable();}-> N21
line 22:	N21 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 5:	N2 -{int local = 0;}-> N3
line 6:	N3 -{int __CPAchecker_TMP_0 = local;}-> N4
line 6:	N4 -{local = local + 1;}-> N5
line 6:	N5 -{__CPAchecker_TMP_0;}-> N6
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 7:	N6 -{intLock();}-> N7
none:	N7 -{default return}-> N0
line 22:	N0 -{Return edge from f to h}-> N22
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 24:	N22 -{kernDispatchEnable();}-> N23
none:	N23 -{default return}-> N18
line 28:	N18 -{Return edge from h to h1}-> N27
Line 0:     N0 -{highlight}-> N0
line 29:	N27 -{int __CPAchecker_TMP_0 = global;}-> N28
Line 0:     N0 -{highlight}-> N0
line 29:	N28 -{global = global + 1;}-> N29
line 29:	N29 -{__CPAchecker_TMP_0;}-> N30
none:	N30 -{default return}-> N24
Line 0:     N0 -{return;}-> N0

