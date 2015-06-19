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
none:	N23 -{INIT GLOBAL VARS}-> N26
none:	N35 -{Function start dummy edge}-> N24
line 35:	N24 -{g()}-> N11
none:	N11 -{Function start dummy edge}-> N12
line 22:	N12 -{int b;}-> N13
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 23:	N13 -{kernDispatchDisable();}-> N14
line 24:	N14 -{[!(b == 0)]}-> N16
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 25:	N16 -{kernDispatchEnable();}-> N18
none:	N18 -{}-> N15
line 28:	N15 -{ldbBreakpoint()}-> N6
none:	N6 -{Function start dummy edge}-> N7
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 17:	N7 -{intLock();}-> N8
line 18:	N8 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 11:	N2 -{gvar = 1;}-> N3
line 12:	N3 -{return 0;}-> N0
line 18:	N0 -{Return edge from f to ldbBreakpoint}-> N9
none:	N9 -{default return}-> N5
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 28:	N5 -{Return edge from ldbBreakpoint to g}-> N20
Line 0:     N0 -{highlight}-> N0
line 31:	N20 -{gvar = 10;}-> N21
none:	N21 -{default return}-> N10
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*intLock[1]*/}-> N0
none:	N23 -{INIT GLOBAL VARS}-> N26
none:	N35 -{Function start dummy edge}-> N24
line 35:	N24 -{g()}-> N11
none:	N11 -{Function start dummy edge}-> N12
line 22:	N12 -{int b;}-> N13
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 23:	N13 -{kernDispatchDisable();}-> N14
line 24:	N14 -{[!(b == 0)]}-> N16
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 25:	N16 -{kernDispatchEnable();}-> N18
none:	N18 -{}-> N15
line 28:	N15 -{ldbBreakpoint()}-> N6
none:	N6 -{Function start dummy edge}-> N7
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 17:	N7 -{intLock();}-> N8
line 18:	N8 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{highlight}-> N0
line 11:	N2 -{gvar = 1;}-> N3
line 12:	N3 -{return 0;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

