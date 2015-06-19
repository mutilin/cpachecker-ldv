1
0
0
0
0
0
1
#
0
int true_unsafe
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N20 -{INIT GLOBAL VARS}-> N24
none:	N31 -{Function start dummy edge}-> N21
line 26:	N21 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 7:	N2 -{[threadDispatchLevel == 0]}-> N5
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 10:	N5 -{kernDispatchDisable();}-> N7
line 11:	N7 -{false_unsafe2 = 1;}-> N8
line 12:	N8 -{true_unsafe = 0;}-> N9
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 13:	N9 -{kernDispatchEnable();}-> N10
none:	N10 -{}-> N3
none:	N3 -{default return}-> N0
line 26:	N0 -{Return edge from f to ldv_main}-> N22
line 27:	N22 -{g()}-> N12
none:	N12 -{Function start dummy edge}-> N13
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 18:	N13 -{kernDispatchDisable();}-> N14
line 19:	N14 -{false_unsafe = 1;}-> N15
line 20:	N15 -{false_unsafe2 = 1;}-> N16
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 21:	N16 -{kernDispatchEnable();}-> N17
Line 0:     N0 -{highlight}-> N0
line 22:	N17 -{true_unsafe = 0;}-> N18
none:	N18 -{default return}-> N11
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*kernDispatchDisable[1]*/}-> N0
none:	N20 -{INIT GLOBAL VARS}-> N24
none:	N31 -{Function start dummy edge}-> N21
line 26:	N21 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 7:	N2 -{[threadDispatchLevel == 0]}-> N5
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 10:	N5 -{kernDispatchDisable();}-> N7
line 11:	N7 -{false_unsafe2 = 1;}-> N8
Line 0:     N0 -{highlight}-> N0
line 12:	N8 -{true_unsafe = 0;}-> N9
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 13:	N9 -{kernDispatchEnable();}-> N10
none:	N10 -{}-> N3
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

