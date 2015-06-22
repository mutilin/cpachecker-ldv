1
0
0
0
0
0
1
#
0
int global2
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N54 -{INIT GLOBAL VARS}-> N57
none:	N64 -{Function start dummy edge}-> N55
line 50:	N55 -{g()}-> N38
none:	N38 -{Function start dummy edge}-> N39
line 36:	N39 -{int p = 0;}-> N40
line 37:	N40 -{int b;}-> N41
line 38:	N41 -{h(p)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 7:	N2 -{int b = 0;}-> N3
line 8:	N3 -{int __CPAchecker_TMP_0 = b;}-> N4
line 8:	N4 -{b = b + 1;}-> N5
line 8:	N5 -{__CPAchecker_TMP_0;}-> N6
line 9:	N6 -{[!(a > b)]}-> N7
none:	N7 -{default return}-> N0
line 38:	N0 -{Return edge from h to g}-> N42
line 39:	N42 -{f(p)}-> N25
none:	N25 -{Function start dummy edge}-> N26
line 27:	N26 -{int b = 0;}-> N27
line 28:	N27 -{int __CPAchecker_TMP_0 = b;}-> N28
line 28:	N28 -{b = b + 1;}-> N29
line 28:	N29 -{__CPAchecker_TMP_0;}-> N30
line 29:	N30 -{[!(a > b)]}-> N31
line 32:	N31 -{return b;}-> N24
line 39:	N24 -{Return edge from f to g}-> N43
line 40:	N43 -{l(p)}-> N13
none:	N13 -{Function start dummy edge}-> N14
line 17:	N14 -{int b = 0;}-> N15
line 18:	N15 -{int __CPAchecker_TMP_0 = b;}-> N16
line 18:	N16 -{b = b + 1;}-> N17
line 18:	N17 -{__CPAchecker_TMP_0;}-> N18
line 19:	N18 -{[!(a > b)]}-> N19
none:	N19 -{default return}-> N12
line 40:	N12 -{Return edge from l to g}-> N44
line 41:	N44 -{[!(b == 0)]}-> N45
Line 0:     N0 -{highlight}-> N0
line 46:	N45 -{int __CPAchecker_TMP_1 = global2;}-> N50
Line 0:     N0 -{highlight}-> N0
line 46:	N50 -{global2 = global2 + 1;}-> N51
line 46:	N51 -{__CPAchecker_TMP_1;}-> N52
none:	N52 -{default return}-> N37
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N54 -{INIT GLOBAL VARS}-> N57
none:	N64 -{Function start dummy edge}-> N55
line 50:	N55 -{g()}-> N38
none:	N38 -{Function start dummy edge}-> N39
line 36:	N39 -{int p = 0;}-> N40
line 37:	N40 -{int b;}-> N41
line 38:	N41 -{h(p)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 7:	N2 -{int b = 0;}-> N3
line 8:	N3 -{int __CPAchecker_TMP_0 = b;}-> N4
line 8:	N4 -{b = b + 1;}-> N5
line 8:	N5 -{__CPAchecker_TMP_0;}-> N6
line 9:	N6 -{[!(a > b)]}-> N7
none:	N7 -{default return}-> N0
line 38:	N0 -{Return edge from h to g}-> N42
line 39:	N42 -{f(p)}-> N25
none:	N25 -{Function start dummy edge}-> N26
line 27:	N26 -{int b = 0;}-> N27
line 28:	N27 -{int __CPAchecker_TMP_0 = b;}-> N28
line 28:	N28 -{b = b + 1;}-> N29
line 28:	N29 -{__CPAchecker_TMP_0;}-> N30
line 29:	N30 -{[!(a > b)]}-> N31
line 32:	N31 -{return b;}-> N24
line 39:	N24 -{Return edge from f to g}-> N43
line 40:	N43 -{l(p)}-> N13
none:	N13 -{Function start dummy edge}-> N14
line 17:	N14 -{int b = 0;}-> N15
line 18:	N15 -{int __CPAchecker_TMP_0 = b;}-> N16
line 18:	N16 -{b = b + 1;}-> N17
line 18:	N17 -{__CPAchecker_TMP_0;}-> N18
line 19:	N18 -{[!(a > b)]}-> N19
none:	N19 -{default return}-> N12
line 40:	N12 -{Return edge from l to g}-> N44
line 41:	N44 -{[!(b == 0)]}-> N45
Line 0:     N0 -{highlight}-> N0
line 46:	N45 -{int __CPAchecker_TMP_1 = global2;}-> N50
Line 0:     N0 -{highlight}-> N0
line 46:	N50 -{global2 = global2 + 1;}-> N51
line 46:	N51 -{__CPAchecker_TMP_1;}-> N52
none:	N52 -{default return}-> N37
Line 0:     N0 -{return;}-> N0

