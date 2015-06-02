1
0
0
0
0
0
1
#
0
int threadDispatchLevel
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N20 -{INIT GLOBAL VARS}-> N24
none:	N29 -{Function start dummy edge}-> N21
line 31:	N22 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 9:	N2 -{int ret;}-> N3
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 12:	N3 -{threadDispatchLevel = 1;}-> N4
line 13:	N4 -{[!(ret == 0)]}-> N5
line 17:	N5 -{unsafe = 0;}-> N9
lines 18-23:	N9 -{while}-> N10
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*kernDispatchDisable[1]*/}-> N0
none:	N20 -{INIT GLOBAL VARS}-> N24
none:	N29 -{Function start dummy edge}-> N21
line 31:	N22 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 9:	N2 -{int ret;}-> N3
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 12:	N3 -{threadDispatchLevel = 1;}-> N4
line 13:	N4 -{[ret == 0]}-> N6
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 14:	N6 -{threadDispatchLevel = 0;}-> N7
line 15:	N7 -{return 28;}-> N0
Line 0:     N0 -{return;}-> N0

