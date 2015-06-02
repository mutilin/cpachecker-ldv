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
none:	N9 -{INIT GLOBAL VARS}-> N15
none:	N20 -{Function start dummy edge}-> N10
line 21:	N10 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 13:	N2 -{kernDispatchThread()}-> N5
none:	N5 -{Function start dummy edge}-> N6
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 17:	N6 -{intLock();}-> N7
none:	N7 -{default return}-> N4
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 13:	N4 -{Return edge from kernDispatchThread to f}-> N3
none:	N3 -{default return}-> N0
line 21:	N0 -{Return edge from f to ldv_main}-> N11
Line 0:     N0 -{highlight}-> N0
line 22:	N11 -{gvar = 1;}-> N12
line 23:	N12 -{kernDispatchThread()}-> N5
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N9 -{INIT GLOBAL VARS}-> N15
none:	N20 -{Function start dummy edge}-> N10
line 21:	N10 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 13:	N2 -{kernDispatchThread()}-> N5
none:	N5 -{Function start dummy edge}-> N6
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 17:	N6 -{intLock();}-> N7
none:	N7 -{default return}-> N4
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 13:	N4 -{Return edge from kernDispatchThread to f}-> N3
none:	N3 -{default return}-> N0
line 21:	N0 -{Return edge from f to ldv_main}-> N11
line 22:	N11 -{gvar = 1;}-> N12
line 23:	N12 -{kernDispatchThread()}-> N5
none:	N5 -{Function start dummy edge}-> N6
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 17:	N6 -{intLock();}-> N7
none:	N7 -{default return}-> N4
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 23:	N4 -{Return edge from kernDispatchThread to ldv_main}-> N13
Line 0:     N0 -{highlight}-> N0
line 24:	N13 -{gvar = 1;}-> N14
none:	N14 -{default return}-> N8
Line 0:     N0 -{return;}-> N0

