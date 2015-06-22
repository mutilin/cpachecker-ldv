2
0
0
0
0
0
2
#
0
int (*p)()
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:1*/}-> N0
Line 0:     N0 -{/*Number of usages      :1*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N16 -{INIT GLOBAL VARS}-> N21
none:	N28 -{Function start dummy edge}-> N17
line 19:	N17 -{f()}-> N6
none:	N6 -{Function start dummy edge}-> N7
line 11:	N7 -{int __CPAchecker_TMP_0 = unsafe;}-> N8
line 11:	N8 -{unsafe = unsafe + 1;}-> N9
line 11:	N9 -{__CPAchecker_TMP_0;}-> N10
none:	N10 -{default return}-> N5
line 19:	N5 -{Return edge from f to ldv_main}-> N18
line 20:	N18 -{main()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{highlight}-> N0
line 6:	N2 -{p = &g;}-> N3
line 7:	N3 -{(*p)();}-> N4
none:	N4 -{default return}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N16 -{INIT GLOBAL VARS}-> N21
none:	N28 -{Function start dummy edge}-> N17
line 19:	N17 -{f()}-> N6
none:	N6 -{Function start dummy edge}-> N7
line 11:	N7 -{int __CPAchecker_TMP_0 = unsafe;}-> N8
line 11:	N8 -{unsafe = unsafe + 1;}-> N9
line 11:	N9 -{__CPAchecker_TMP_0;}-> N10
none:	N10 -{default return}-> N5
line 19:	N5 -{Return edge from f to ldv_main}-> N18
line 20:	N18 -{main()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{highlight}-> N0
line 6:	N2 -{p = &g;}-> N3
line 7:	N3 -{(*p)();}-> N4
none:	N4 -{default return}-> N0
Line 0:     N0 -{return;}-> N0

#
0
int unsafe
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N16 -{INIT GLOBAL VARS}-> N21
none:	N28 -{Function start dummy edge}-> N17
line 19:	N17 -{f()}-> N6
none:	N6 -{Function start dummy edge}-> N7
Line 0:     N0 -{highlight}-> N0
line 11:	N7 -{int __CPAchecker_TMP_0 = unsafe;}-> N8
Line 0:     N0 -{highlight}-> N0
line 11:	N8 -{unsafe = unsafe + 1;}-> N9
line 11:	N9 -{__CPAchecker_TMP_0;}-> N10
none:	N10 -{default return}-> N5
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N16 -{INIT GLOBAL VARS}-> N21
none:	N28 -{Function start dummy edge}-> N17
line 19:	N17 -{f()}-> N6
none:	N6 -{Function start dummy edge}-> N7
Line 0:     N0 -{highlight}-> N0
line 11:	N7 -{int __CPAchecker_TMP_0 = unsafe;}-> N8
Line 0:     N0 -{highlight}-> N0
line 11:	N8 -{unsafe = unsafe + 1;}-> N9
line 11:	N9 -{__CPAchecker_TMP_0;}-> N10
none:	N10 -{default return}-> N5
Line 0:     N0 -{return;}-> N0

