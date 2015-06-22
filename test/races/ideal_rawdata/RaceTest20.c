2
0
0
0
0
0
2
#
0
int global
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N10 -{INIT GLOBAL VARS}-> N14
none:	N20 -{Function start dummy edge}-> N11
line 21:	N11 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 12:	N2 -{unsafe = false_unsafe;}-> N3
none:	N3 -{default return}-> N0
line 21:	N0 -{Return edge from f to ldv_main}-> N12
line 22:	N12 -{g()}-> N5
none:	N5 -{Function start dummy edge}-> N6
line 16:	N6 -{unsafe = global;}-> N7
Line 0:     N0 -{highlight}-> N0
line 17:	N7 -{global = false_unsafe;}-> N8
none:	N8 -{default return}-> N4
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N10 -{INIT GLOBAL VARS}-> N14
none:	N20 -{Function start dummy edge}-> N11
line 21:	N11 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 12:	N2 -{unsafe = false_unsafe;}-> N3
none:	N3 -{default return}-> N0
line 21:	N0 -{Return edge from f to ldv_main}-> N12
line 22:	N12 -{g()}-> N5
none:	N5 -{Function start dummy edge}-> N6
Line 0:     N0 -{highlight}-> N0
line 16:	N6 -{unsafe = global;}-> N7
line 17:	N7 -{global = false_unsafe;}-> N8
none:	N8 -{default return}-> N4
Line 0:     N0 -{return;}-> N0

#
0
int unsafe
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N10 -{INIT GLOBAL VARS}-> N14
none:	N20 -{Function start dummy edge}-> N11
line 21:	N11 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
Line 0:     N0 -{highlight}-> N0
line 12:	N2 -{unsafe = false_unsafe;}-> N3
none:	N3 -{default return}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N10 -{INIT GLOBAL VARS}-> N14
none:	N20 -{Function start dummy edge}-> N11
line 21:	N11 -{f()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 12:	N2 -{unsafe = false_unsafe;}-> N3
none:	N3 -{default return}-> N0
line 21:	N0 -{Return edge from f to ldv_main}-> N12
line 22:	N12 -{g()}-> N5
none:	N5 -{Function start dummy edge}-> N6
Line 0:     N0 -{highlight}-> N0
line 16:	N6 -{unsafe = global;}-> N7
line 17:	N7 -{global = false_unsafe;}-> N8
none:	N8 -{default return}-> N4
Line 0:     N0 -{return;}-> N0

