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
none:	N21 -{INIT GLOBAL VARS}-> N26
none:	N31 -{Function start dummy edge}-> N22
line 20:	N22 -{f()}-> N15
none:	N15 -{Function start dummy edge}-> N16
Line 0:     N0 -{highlight}-> N0
line 16:	N16 -{int __CPAchecker_TMP_0 = unsafe;}-> N17
Line 0:     N0 -{highlight}-> N0
line 16:	N17 -{unsafe = unsafe + 1;}-> N18
line 16:	N18 -{__CPAchecker_TMP_0;}-> N19
none:	N19 -{default return}-> N14
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N21 -{INIT GLOBAL VARS}-> N26
none:	N31 -{Function start dummy edge}-> N22
line 20:	N22 -{f()}-> N15
none:	N15 -{Function start dummy edge}-> N16
Line 0:     N0 -{highlight}-> N0
line 16:	N16 -{int __CPAchecker_TMP_0 = unsafe;}-> N17
Line 0:     N0 -{highlight}-> N0
line 16:	N17 -{unsafe = unsafe + 1;}-> N18
line 16:	N18 -{__CPAchecker_TMP_0;}-> N19
none:	N19 -{default return}-> N14
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

