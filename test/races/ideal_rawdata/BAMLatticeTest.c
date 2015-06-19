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
none:	N6 -{INIT GLOBAL VARS}-> N11
none:	N14 -{Function start dummy edge}-> N7
line 11:	N7 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{highlight}-> N0
line 6:	N2 -{gvar = 1;}-> N3
line 7:	N3 -{return 0;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*kernDispatchDisable[1]*/}-> N0
none:	N6 -{INIT GLOBAL VARS}-> N11
none:	N14 -{Function start dummy edge}-> N7
line 11:	N7 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{highlight}-> N0
line 6:	N2 -{gvar = 1;}-> N3
line 7:	N3 -{return 0;}-> N0
line 11:	N0 -{Return edge from f to ldv_main}-> N8
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 12:	N8 -{kernDispatchDisable();}-> N9
line 13:	N9 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{highlight}-> N0
line 6:	N2 -{gvar = 1;}-> N3
line 7:	N3 -{return 0;}-> N0
Line 0:     N0 -{return;}-> N0

