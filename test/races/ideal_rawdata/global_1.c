1
0
0
0
0
0
1
#
0
int gvar
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N13 -{INIT GLOBAL VARS}-> N17
none:	N25 -{Function start dummy edge}-> N14
line 21:	N14 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{highlight}-> N0
line 9:	N2 -{gvar = 1;}-> N3
line 10:	N3 -{return 0;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*kernDispatchDisable[1]*/}-> N0
none:	N13 -{INIT GLOBAL VARS}-> N17
none:	N25 -{Function start dummy edge}-> N14
line 21:	N14 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 9:	N2 -{gvar = 1;}-> N3
line 10:	N3 -{return 0;}-> N0
line 21:	N0 -{Return edge from f to ldv_main}-> N15
line 22:	N15 -{g()}-> N6
none:	N6 -{Function start dummy edge}-> N7
line 14:	N7 -{int b;}-> N8
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 15:	N8 -{kernDispatchDisable();}-> N9
Line 0:     N0 -{highlight}-> N0
line 16:	N9 -{b = gvar;}-> N10
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 17:	N10 -{kernDispatchEnable();}-> N11
none:	N11 -{default return}-> N5
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

