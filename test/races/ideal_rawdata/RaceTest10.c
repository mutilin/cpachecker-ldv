1
0
0
0
0
0
1
#
0
int false_unsafe
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N11 -{INIT GLOBAL VARS}-> N15
none:	N19 -{Function start dummy edge}-> N12
Line 0:     N0 -{highlight}-> N0
line 16:	N12 -{false_unsafe = 0;}-> N13
line 17:	N13 -{f()}-> N1
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*kernDispatchDisable[1]*/}-> N0
none:	N11 -{INIT GLOBAL VARS}-> N15
none:	N19 -{Function start dummy edge}-> N12
line 16:	N12 -{false_unsafe = 0;}-> N13
line 17:	N13 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 6:	N2 -{int b = 0;}-> N3
line 7:	N3 -{b = unknown;}-> N4
line 8:	N4 -{[!(b == 0)]}-> N6
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 9:	N6 -{kernDispatchDisable();}-> N7
Line 0:     N0 -{highlight}-> N0
line 10:	N7 -{false_unsafe = 1;}-> N8
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 11:	N8 -{kernDispatchDisable();}-> N9
none:	N9 -{}-> N5
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

