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
none:	N58 -{INIT GLOBAL VARS}-> N67
none:	N72 -{Function start dummy edge}-> N59
line 45:	N60 -{f(b)}-> N40
none:	N40 -{Function start dummy edge}-> N41
line 31:	N41 -{int j;}-> N42
line 31:	N42 -{g(i)}-> N22
none:	N22 -{Function start dummy edge}-> N23
line 18:	N23 -{int j;}-> N24
line 18:	N24 -{h(i)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 4:	N2 -{int j;}-> N3
line 4:	N3 -{j = h(i);}-> N4
line 5:	N4 -{int __CPAchecker_TMP_0 = i;}-> N5
line 5:	N5 -{i = i + 1;}-> N6
line 5:	N6 -{__CPAchecker_TMP_0;}-> N7
line 6:	N7 -{[!(j > i)]}-> N10
line 9:	N10 -{j = i - j;}-> N12
Line 0:     N0 -{highlight}-> N0
line 10:	N12 -{int __CPAchecker_TMP_1 = global;}-> N13
Line 0:     N0 -{highlight}-> N0
line 10:	N13 -{global = global + 1;}-> N14
line 10:	N14 -{__CPAchecker_TMP_1;}-> N15
none:	N15 -{}-> N8
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*intLock[1]*/}-> N0
none:	N58 -{INIT GLOBAL VARS}-> N67
none:	N72 -{Function start dummy edge}-> N59
line 45:	N60 -{f(b)}-> N40
none:	N40 -{Function start dummy edge}-> N41
line 31:	N41 -{int j;}-> N42
line 31:	N42 -{g(i)}-> N22
none:	N22 -{Function start dummy edge}-> N23
line 18:	N23 -{int j;}-> N24
line 18:	N24 -{h(i)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 4:	N2 -{int j;}-> N3
line 4:	N3 -{j = h(i);}-> N4
line 5:	N4 -{int __CPAchecker_TMP_0 = i;}-> N5
line 5:	N5 -{i = i + 1;}-> N6
line 5:	N6 -{__CPAchecker_TMP_0;}-> N7
line 6:	N7 -{[!(j > i)]}-> N10
line 9:	N10 -{j = i - j;}-> N12
Line 0:     N0 -{highlight}-> N0
line 10:	N12 -{int __CPAchecker_TMP_1 = global;}-> N13
Line 0:     N0 -{highlight}-> N0
line 10:	N13 -{global = global + 1;}-> N14
line 10:	N14 -{__CPAchecker_TMP_1;}-> N15
none:	N15 -{}-> N8
line 12:	N8 -{j = h(j);}-> N16
line 13:	N16 -{int __CPAchecker_TMP_2 = j;}-> N17
line 13:	N17 -{j = j + 1;}-> N18
line 13:	N18 -{__CPAchecker_TMP_2;}-> N19
line 14:	N19 -{return j;}-> N0
line 18:	N0 -{Return edge from h to g}-> N25
line 19:	N25 -{int __CPAchecker_TMP_0 = i;}-> N26
line 19:	N26 -{i = i + 1;}-> N27
line 19:	N27 -{__CPAchecker_TMP_0;}-> N28
line 20:	N28 -{[!(j > i)]}-> N31
line 23:	N31 -{j = i - j;}-> N33
none:	N33 -{}-> N29
line 25:	N29 -{h(j)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 4:	N2 -{int j;}-> N3
line 4:	N3 -{j = h(i);}-> N4
line 5:	N4 -{int __CPAchecker_TMP_0 = i;}-> N5
line 5:	N5 -{i = i + 1;}-> N6
line 5:	N6 -{__CPAchecker_TMP_0;}-> N7
line 6:	N7 -{[!(j > i)]}-> N10
line 9:	N10 -{j = i - j;}-> N12
Line 0:     N0 -{highlight}-> N0
line 10:	N12 -{int __CPAchecker_TMP_1 = global;}-> N13
Line 0:     N0 -{highlight}-> N0
line 10:	N13 -{global = global + 1;}-> N14
line 10:	N14 -{__CPAchecker_TMP_1;}-> N15
none:	N15 -{}-> N8
line 12:	N8 -{j = h(j);}-> N16
line 13:	N16 -{int __CPAchecker_TMP_2 = j;}-> N17
line 13:	N17 -{j = j + 1;}-> N18
line 13:	N18 -{__CPAchecker_TMP_2;}-> N19
line 14:	N19 -{return j;}-> N0
line 25:	N0 -{Return edge from h to g}-> N34
line 26:	N34 -{int __CPAchecker_TMP_1 = j;}-> N35
line 26:	N35 -{j = j + 1;}-> N36
line 26:	N36 -{__CPAchecker_TMP_1;}-> N37
line 27:	N37 -{return j;}-> N21
line 31:	N21 -{Return edge from g to f}-> N43
line 32:	N43 -{int __CPAchecker_TMP_0 = i;}-> N44
line 32:	N44 -{i = i + 1;}-> N45
line 32:	N45 -{__CPAchecker_TMP_0;}-> N46
line 33:	N46 -{[!(j > i)]}-> N49
line 36:	N49 -{j = i - j;}-> N51
none:	N51 -{}-> N47
line 38:	N47 -{g(j)}-> N22
none:	N22 -{Function start dummy edge}-> N23
line 18:	N23 -{int j;}-> N24
line 18:	N24 -{h(i)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 4:	N2 -{int j;}-> N3
line 4:	N3 -{j = h(i);}-> N4
line 5:	N4 -{int __CPAchecker_TMP_0 = i;}-> N5
line 5:	N5 -{i = i + 1;}-> N6
line 5:	N6 -{__CPAchecker_TMP_0;}-> N7
line 6:	N7 -{[!(j > i)]}-> N10
line 9:	N10 -{j = i - j;}-> N12
Line 0:     N0 -{highlight}-> N0
line 10:	N12 -{int __CPAchecker_TMP_1 = global;}-> N13
Line 0:     N0 -{highlight}-> N0
line 10:	N13 -{global = global + 1;}-> N14
line 10:	N14 -{__CPAchecker_TMP_1;}-> N15
none:	N15 -{}-> N8
line 12:	N8 -{j = h(j);}-> N16
line 13:	N16 -{int __CPAchecker_TMP_2 = j;}-> N17
line 13:	N17 -{j = j + 1;}-> N18
line 13:	N18 -{__CPAchecker_TMP_2;}-> N19
line 14:	N19 -{return j;}-> N0
line 18:	N0 -{Return edge from h to g}-> N25
line 19:	N25 -{int __CPAchecker_TMP_0 = i;}-> N26
line 19:	N26 -{i = i + 1;}-> N27
line 19:	N27 -{__CPAchecker_TMP_0;}-> N28
line 20:	N28 -{[!(j > i)]}-> N31
line 23:	N31 -{j = i - j;}-> N33
none:	N33 -{}-> N29
line 25:	N29 -{h(j)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 4:	N2 -{int j;}-> N3
line 4:	N3 -{j = h(i);}-> N4
line 5:	N4 -{int __CPAchecker_TMP_0 = i;}-> N5
line 5:	N5 -{i = i + 1;}-> N6
line 5:	N6 -{__CPAchecker_TMP_0;}-> N7
line 6:	N7 -{[!(j > i)]}-> N10
line 9:	N10 -{j = i - j;}-> N12
Line 0:     N0 -{highlight}-> N0
line 10:	N12 -{int __CPAchecker_TMP_1 = global;}-> N13
Line 0:     N0 -{highlight}-> N0
line 10:	N13 -{global = global + 1;}-> N14
line 10:	N14 -{__CPAchecker_TMP_1;}-> N15
none:	N15 -{}-> N8
line 12:	N8 -{j = h(j);}-> N16
line 13:	N16 -{int __CPAchecker_TMP_2 = j;}-> N17
line 13:	N17 -{j = j + 1;}-> N18
line 13:	N18 -{__CPAchecker_TMP_2;}-> N19
line 14:	N19 -{return j;}-> N0
line 25:	N0 -{Return edge from h to g}-> N34
line 26:	N34 -{int __CPAchecker_TMP_1 = j;}-> N35
line 26:	N35 -{j = j + 1;}-> N36
line 26:	N36 -{__CPAchecker_TMP_1;}-> N37
line 27:	N37 -{return j;}-> N21
line 38:	N21 -{Return edge from g to f}-> N52
line 39:	N52 -{int __CPAchecker_TMP_1 = j;}-> N53
line 39:	N53 -{j = j + 1;}-> N54
line 39:	N54 -{__CPAchecker_TMP_1;}-> N55
line 40:	N55 -{return j;}-> N39
line 45:	N39 -{Return edge from f to ldv_main}-> N61
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 46:	N61 -{intLock();}-> N62
line 47:	N63 -{b = b + 1;}-> N64
line 47:	N64 -{f(__CPAchecker_TMP_0)}-> N40
none:	N40 -{Function start dummy edge}-> N41
line 31:	N41 -{int j;}-> N42
line 31:	N42 -{g(i)}-> N22
none:	N22 -{Function start dummy edge}-> N23
line 18:	N23 -{int j;}-> N24
line 18:	N24 -{h(i)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 4:	N2 -{int j;}-> N3
line 4:	N3 -{j = h(i);}-> N4
line 5:	N4 -{int __CPAchecker_TMP_0 = i;}-> N5
line 5:	N5 -{i = i + 1;}-> N6
line 5:	N6 -{__CPAchecker_TMP_0;}-> N7
line 6:	N7 -{[!(j > i)]}-> N10
line 9:	N10 -{j = i - j;}-> N12
Line 0:     N0 -{highlight}-> N0
line 10:	N12 -{int __CPAchecker_TMP_1 = global;}-> N13
Line 0:     N0 -{highlight}-> N0
line 10:	N13 -{global = global + 1;}-> N14
line 10:	N14 -{__CPAchecker_TMP_1;}-> N15
none:	N15 -{}-> N8
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

