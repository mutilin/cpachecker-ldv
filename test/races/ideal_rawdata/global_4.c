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
none:	N16 -{INIT GLOBAL VARS}-> N20
none:	N25 -{Function start dummy edge}-> N17
line 26:	N17 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{highlight}-> N0
line 11:	N2 -{gvar = 1;}-> N3
none:	N3 -{default return}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*kernDispatchDisable[1]*/}-> N0
none:	N16 -{INIT GLOBAL VARS}-> N20
none:	N25 -{Function start dummy edge}-> N17
line 26:	N17 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 11:	N2 -{gvar = 1;}-> N3
none:	N3 -{default return}-> N0
line 26:	N0 -{Return edge from f to ldv_main}-> N18
line 27:	N18 -{h()}-> N5
none:	N5 -{Function start dummy edge}-> N6
line 15:	N6 -{g()}-> N9
none:	N9 -{Function start dummy edge}-> N10
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 19:	N10 -{kernDispatchDisable();}-> N11
line 20:	N11 -{h();}-> N12
Line 0:     N0 -{highlight}-> N0
line 21:	N12 -{gvar = 1;}-> N13
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 22:	N13 -{kernDispatchEnable();}-> N14
none:	N14 -{default return}-> N8
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

