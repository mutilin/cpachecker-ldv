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
none:	N20 -{INIT GLOBAL VARS}-> N28
none:	N36 -{Function start dummy edge}-> N21
Line 0:     N0 -{/*Change states for locks spl*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 36:	N22 -{splbio()}-> N7
none:	N7 -{Function start dummy edge}-> N8
line 20:	N8 -{int tmp;}-> N9
line 21:	N9 -{spl()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 13:	N2 -{int tmp;}-> N3
Line 0:     N0 -{/*Change states for locks pthread_mutex_lock(&ipl_mutex)*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 14:	N3 -{tmp = pthread_mutex_lock(&ipl_mutex);}-> N4
line 15:	N4 -{return tmp;}-> N0
line 21:	N0 -{Return edge from spl to splbio}-> N10
line 22:	N10 -{return tmp;}-> N6
line 36:	N6 -{Return edge from splbio to ldv_main}-> N23
line 37:	N23 -{gvar = 0;}-> N24
Line 0:     N0 -{/*Change states for locks spl*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 38:	N24 -{splx(t)}-> N13
none:	N13 -{Function start dummy edge}-> N14
line 27:	N14 -{[x == 0]}-> N16
Line 0:     N0 -{/*Change states for locks pthread_mutex_lock(&ipl_mutex)*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 28:	N16 -{pthread_mutex_unlock(&ipl_mutex);}-> N17
line 29:	N17 -{return;}-> N12
line 38:	N12 -{Return edge from splx to ldv_main}-> N25
Line 0:     N0 -{highlight}-> N0
line 39:	N25 -{gvar = 1;}-> N26
line 40:	N26 -{return;}-> N19

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*pthread_mutex_lock(&ipl_mutex)[1]*/}-> N0
none:	N20 -{INIT GLOBAL VARS}-> N28
none:	N36 -{Function start dummy edge}-> N21
Line 0:     N0 -{/*Change states for locks spl*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 36:	N22 -{splbio()}-> N7
none:	N7 -{Function start dummy edge}-> N8
line 20:	N8 -{int tmp;}-> N9
line 21:	N9 -{spl()}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 13:	N2 -{int tmp;}-> N3
Line 0:     N0 -{/*Change states for locks pthread_mutex_lock(&ipl_mutex)*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 14:	N3 -{tmp = pthread_mutex_lock(&ipl_mutex);}-> N4
line 15:	N4 -{return tmp;}-> N0
line 21:	N0 -{Return edge from spl to splbio}-> N10
line 22:	N10 -{return tmp;}-> N6
line 36:	N6 -{Return edge from splbio to ldv_main}-> N23
line 37:	N23 -{gvar = 0;}-> N24
Line 0:     N0 -{/*Change states for locks spl*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 38:	N24 -{splx(t)}-> N13
none:	N13 -{Function start dummy edge}-> N14
line 27:	N14 -{[!(x == 0)]}-> N15
none:	N15 -{default return}-> N12
line 38:	N12 -{Return edge from splx to ldv_main}-> N25
Line 0:     N0 -{highlight}-> N0
line 39:	N25 -{gvar = 1;}-> N26
line 40:	N26 -{return;}-> N19

