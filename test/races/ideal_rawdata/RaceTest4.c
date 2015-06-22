5
0
0
0
1
0
6
###
0
int *a
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N34 -{INIT GLOBAL VARS}-> N37
none:	N46 -{Function start dummy edge}-> N35
line 46:	N35 -{main()}-> N26
none:	N26 -{Function start dummy edge}-> N27
line 38:	N27 -{global = 0;}-> N28
line 39:	N28 -{true_unsafe4 = 0;}-> N29
Line 0:     N0 -{highlight}-> N0
line 40:	N29 -{((struct A *)23)->a = 0;}-> N30
line 41:	N30 -{f(global)}-> N1
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N34 -{INIT GLOBAL VARS}-> N37
none:	N46 -{Function start dummy edge}-> N35
line 46:	N35 -{main()}-> N26
none:	N26 -{Function start dummy edge}-> N27
line 38:	N27 -{global = 0;}-> N28
line 39:	N28 -{true_unsafe4 = 0;}-> N29
line 40:	N29 -{((struct A *)23)->a = 0;}-> N30
line 41:	N30 -{f(global)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 13:	N2 -{[!(global != 0)]}-> N3
Line 0:     N0 -{highlight}-> N0
line 22:	N3 -{[!((((struct A *)23)->a) != 0)]}-> N12
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

#
0
int global
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N34 -{INIT GLOBAL VARS}-> N37
none:	N46 -{Function start dummy edge}-> N35
line 46:	N35 -{main()}-> N26
none:	N26 -{Function start dummy edge}-> N27
Line 0:     N0 -{highlight}-> N0
line 38:	N27 -{global = 0;}-> N28
line 39:	N28 -{true_unsafe4 = 0;}-> N29
line 40:	N29 -{((struct A *)23)->a = 0;}-> N30
line 41:	N30 -{f(global)}-> N1
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N34 -{INIT GLOBAL VARS}-> N37
none:	N46 -{Function start dummy edge}-> N35
line 46:	N35 -{main()}-> N26
none:	N26 -{Function start dummy edge}-> N27
line 38:	N27 -{global = 0;}-> N28
line 39:	N28 -{true_unsafe4 = 0;}-> N29
line 40:	N29 -{((struct A *)23)->a = 0;}-> N30
line 41:	N30 -{f(global)}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{highlight}-> N0
line 13:	N2 -{[!(global != 0)]}-> N3
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

#
0
int true_unsafe
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N34 -{INIT GLOBAL VARS}-> N37
none:	N46 -{Function start dummy edge}-> N35
line 46:	N35 -{main()}-> N26
none:	N26 -{Function start dummy edge}-> N27
line 38:	N27 -{global = 0;}-> N28
line 39:	N28 -{true_unsafe4 = 0;}-> N29
line 40:	N29 -{((struct A *)23)->a = 0;}-> N30
line 41:	N30 -{f(global)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 13:	N2 -{[!(global != 0)]}-> N3
line 22:	N3 -{[!((((struct A *)23)->a) != 0)]}-> N12
line 30:	N12 -{[!(a != 0)]}-> N20
none:	N20 -{default return}-> N0
line 41:	N0 -{Return edge from f to main}-> N31
Line 0:     N0 -{highlight}-> N0
line 42:	N31 -{true_unsafe = 0;}-> N32
none:	N32 -{default return}-> N25
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*intLock[1]*/}-> N0
none:	N34 -{INIT GLOBAL VARS}-> N37
none:	N46 -{Function start dummy edge}-> N35
line 46:	N35 -{main()}-> N26
none:	N26 -{Function start dummy edge}-> N27
line 38:	N27 -{global = 0;}-> N28
line 39:	N28 -{true_unsafe4 = 0;}-> N29
line 40:	N29 -{((struct A *)23)->a = 0;}-> N30
line 41:	N30 -{f(global)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 13:	N2 -{[global != 0]}-> N4
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 14:	N4 -{intLock();}-> N5
Line 0:     N0 -{highlight}-> N0
line 15:	N5 -{true_unsafe = 1;}-> N6
line 16:	N6 -{true_unsafe2 = 1;}-> N7
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 17:	N7 -{intUnlock();}-> N8
line 18:	N8 -{[!(global == 0)]}-> N9
none:	N9 -{}-> N3
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

#
0
int true_unsafe2
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N34 -{INIT GLOBAL VARS}-> N37
none:	N46 -{Function start dummy edge}-> N35
line 46:	N35 -{main()}-> N26
none:	N26 -{Function start dummy edge}-> N27
line 38:	N27 -{global = 0;}-> N28
line 39:	N28 -{true_unsafe4 = 0;}-> N29
line 40:	N29 -{((struct A *)23)->a = 0;}-> N30
line 41:	N30 -{f(global)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 13:	N2 -{[global != 0]}-> N4
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 14:	N4 -{intLock();}-> N5
line 15:	N5 -{true_unsafe = 1;}-> N6
line 16:	N6 -{true_unsafe2 = 1;}-> N7
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 17:	N7 -{intUnlock();}-> N8
line 18:	N8 -{[global == 0]}-> N10
Line 0:     N0 -{highlight}-> N0
line 19:	N10 -{true_unsafe2 = 0;}-> N11
none:	N11 -{}-> N3
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*intLock[1]*/}-> N0
none:	N34 -{INIT GLOBAL VARS}-> N37
none:	N46 -{Function start dummy edge}-> N35
line 46:	N35 -{main()}-> N26
none:	N26 -{Function start dummy edge}-> N27
line 38:	N27 -{global = 0;}-> N28
line 39:	N28 -{true_unsafe4 = 0;}-> N29
line 40:	N29 -{((struct A *)23)->a = 0;}-> N30
line 41:	N30 -{f(global)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 13:	N2 -{[global != 0]}-> N4
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 14:	N4 -{intLock();}-> N5
line 15:	N5 -{true_unsafe = 1;}-> N6
Line 0:     N0 -{highlight}-> N0
line 16:	N6 -{true_unsafe2 = 1;}-> N7
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 17:	N7 -{intUnlock();}-> N8
line 18:	N8 -{[!(global == 0)]}-> N9
none:	N9 -{}-> N3
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

#
0
int true_unsafe3
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N34 -{INIT GLOBAL VARS}-> N37
none:	N46 -{Function start dummy edge}-> N35
line 46:	N35 -{main()}-> N26
none:	N26 -{Function start dummy edge}-> N27
line 38:	N27 -{global = 0;}-> N28
line 39:	N28 -{true_unsafe4 = 0;}-> N29
line 40:	N29 -{((struct A *)23)->a = 0;}-> N30
line 41:	N30 -{f(global)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 13:	N2 -{[!(global != 0)]}-> N3
line 22:	N3 -{[(((struct A *)23)->a) != 0]}-> N13
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 23:	N13 -{intLock();}-> N14
line 24:	N14 -{true_unsafe3 = 0;}-> N15
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 25:	N15 -{intUnlock();}-> N16
line 26:	N16 -{[(((struct A *)23)->a) == 0]}-> N18
Line 0:     N0 -{highlight}-> N0
line 27:	N18 -{true_unsafe3 = 0;}-> N19
none:	N19 -{}-> N12
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*intLock[1]*/}-> N0
none:	N34 -{INIT GLOBAL VARS}-> N37
none:	N46 -{Function start dummy edge}-> N35
line 46:	N35 -{main()}-> N26
none:	N26 -{Function start dummy edge}-> N27
line 38:	N27 -{global = 0;}-> N28
line 39:	N28 -{true_unsafe4 = 0;}-> N29
line 40:	N29 -{((struct A *)23)->a = 0;}-> N30
line 41:	N30 -{f(global)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 13:	N2 -{[!(global != 0)]}-> N3
line 22:	N3 -{[(((struct A *)23)->a) != 0]}-> N13
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 23:	N13 -{intLock();}-> N14
Line 0:     N0 -{highlight}-> N0
line 24:	N14 -{true_unsafe3 = 0;}-> N15
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 25:	N15 -{intUnlock();}-> N16
line 26:	N16 -{[!((((struct A *)23)->a) == 0)]}-> N17
none:	N17 -{}-> N12
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

#
0
int true_unsafe4
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N34 -{INIT GLOBAL VARS}-> N37
none:	N46 -{Function start dummy edge}-> N35
line 46:	N35 -{main()}-> N26
none:	N26 -{Function start dummy edge}-> N27
line 38:	N27 -{global = 0;}-> N28
Line 0:     N0 -{highlight}-> N0
line 39:	N28 -{true_unsafe4 = 0;}-> N29
line 40:	N29 -{((struct A *)23)->a = 0;}-> N30
line 41:	N30 -{f(global)}-> N1
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*intLock[1]*/}-> N0
none:	N34 -{INIT GLOBAL VARS}-> N37
none:	N46 -{Function start dummy edge}-> N35
line 46:	N35 -{main()}-> N26
none:	N26 -{Function start dummy edge}-> N27
line 38:	N27 -{global = 0;}-> N28
line 39:	N28 -{true_unsafe4 = 0;}-> N29
line 40:	N29 -{((struct A *)23)->a = 0;}-> N30
line 41:	N30 -{f(global)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 13:	N2 -{[!(global != 0)]}-> N3
line 22:	N3 -{[!((((struct A *)23)->a) != 0)]}-> N12
line 30:	N12 -{[a != 0]}-> N21
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 31:	N21 -{intLock();}-> N22
Line 0:     N0 -{highlight}-> N0
line 32:	N22 -{true_unsafe4 = 1;}-> N23
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 33:	N23 -{intUnlock();}-> N24
none:	N24 -{}-> N20
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

