1
1
0
2
1
0
5
0
0
0
1
1
0
2
2
intLock[1]
kernDispatchDisable[1]
###
0
struct testStruct *a
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usages:4*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*intLock[1]*/}-> N0
Line 0:     N0 -{ldv_main()}-> N0
Line 0:     N0 -{Function start dummy edge}-> N0
Line 36:     N0 -{intLock[1]}-> N0
Line 37:     N0 -{f((?.a));}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
Line 0:     N0 -{ldv_main()}-> N0
Line 0:     N0 -{Function start dummy edge}-> N0
Line 42:     N0 -{(?.a) = ...;}-> N0
Line 0:     N0 -{return;}-> N0
##f
1
int *c
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usages:3*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*kernDispatchDisable[1]*/}-> N0
Line 0:     N0 -{ldv_main()}-> N0
Line 0:     N0 -{Function start dummy edge}-> N0
Line 31:     N0 -{f()}-> N0
Line 0:     N0 -{Function start dummy edge}-> N0
Line 16:     N0 -{kernDispatchDisable[1]}-> N0
Line 19:     N0 -{*c = ...;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*intLock[1]*/}-> N0
Line 0:     N0 -{ldv_main()}-> N0
Line 0:     N0 -{Function start dummy edge}-> N0
Line 31:     N0 -{f()}-> N0
Line 0:     N0 -{Function start dummy edge}-> N0
Line 14:     N0 -{intLock[1]}-> N0
Line 15:     N0 -{*c = ...;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
