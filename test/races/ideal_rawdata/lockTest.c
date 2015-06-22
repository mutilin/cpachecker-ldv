0
0
0
2
1
0
3
###
0
struct testStruct *a
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N13 -{INIT GLOBAL VARS}-> N33
none:	N40 -{Function start dummy edge}-> N14
line 34:	N18 -{f(0)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 16:	N2 -{int *c = &t;}-> N3
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 17:	N3 -{intLock();}-> N4
line 18:	N4 -{*c = 2;}-> N5
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 19:	N5 -{kernDispatchDisable();}-> N6
line 20:	N6 -{*c = 4;}-> N7
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 21:	N7 -{intUnlock();}-> N8
line 22:	N8 -{*c = 3;}-> N9
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 23:	N9 -{kernDispatchEnable();}-> N10
line 24:	N10 -{return 0;}-> N0
line 34:	N0 -{Return edge from f to ldv_main}-> N19
line 37:	N19 -{q = *temp;}-> N20
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 39:	N22 -{intLock();}-> N23
line 40:	N23 -{temp = sdlFirst(&(s->a));}-> N24
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 41:	N24 -{intUnlock();}-> N25
none:	N25 -{}-> N21
line 43:	N21 -{temp2 = sdlFirst(temp);}-> N26
line 44:	N26 -{temp2 = sdlFirst(temp2);}-> N27
line 46:	N27 -{*temp2 = 1;}-> N28
Line 0:     N0 -{/*Change states for locks queLOCK(a)*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 49:	N28 -{queLOCK(a);}-> N29
line 50:	N29 -{p = 1;}-> N30
Line 0:     N0 -{/*Change states for locks queLOCK(q)*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 51:	N30 -{queUNLOCK(q);}-> N31
line 52:	N31 -{p = 2;}-> N32
none:	N32 -{default return}-> N12

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N13 -{INIT GLOBAL VARS}-> N33
none:	N40 -{Function start dummy edge}-> N14
line 34:	N18 -{f(0)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 16:	N2 -{int *c = &t;}-> N3
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 17:	N3 -{intLock();}-> N4
line 18:	N4 -{*c = 2;}-> N5
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 19:	N5 -{kernDispatchDisable();}-> N6
line 20:	N6 -{*c = 4;}-> N7
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 21:	N7 -{intUnlock();}-> N8
line 22:	N8 -{*c = 3;}-> N9
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 23:	N9 -{kernDispatchEnable();}-> N10
line 24:	N10 -{return 0;}-> N0
line 34:	N0 -{Return edge from f to ldv_main}-> N19
line 37:	N19 -{q = *temp;}-> N20
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 39:	N22 -{intLock();}-> N23
line 40:	N23 -{temp = sdlFirst(&(s->a));}-> N24
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 41:	N24 -{intUnlock();}-> N25
none:	N25 -{}-> N21
line 43:	N21 -{temp2 = sdlFirst(temp);}-> N26
line 44:	N26 -{temp2 = sdlFirst(temp2);}-> N27
line 46:	N27 -{*temp2 = 1;}-> N28
Line 0:     N0 -{/*Change states for locks queLOCK(a)*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 49:	N28 -{queLOCK(a);}-> N29
line 50:	N29 -{p = 1;}-> N30
Line 0:     N0 -{/*Change states for locks queLOCK(q)*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 51:	N30 -{queUNLOCK(q);}-> N31
line 52:	N31 -{p = 2;}-> N32
none:	N32 -{default return}-> N12

##f
1
int *c
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*intLock[1]*/}-> N0
none:	N13 -{INIT GLOBAL VARS}-> N33
none:	N40 -{Function start dummy edge}-> N14
line 34:	N18 -{f(0)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 16:	N2 -{int *c = &t;}-> N3
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 17:	N3 -{intLock();}-> N4
Line 0:     N0 -{highlight}-> N0
line 18:	N4 -{*c = 2;}-> N5
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 19:	N5 -{kernDispatchDisable();}-> N6
line 20:	N6 -{*c = 4;}-> N7
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 21:	N7 -{intUnlock();}-> N8
line 22:	N8 -{*c = 3;}-> N9
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 23:	N9 -{kernDispatchEnable();}-> N10
line 24:	N10 -{return 0;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*kernDispatchDisable[1]*/}-> N0
none:	N13 -{INIT GLOBAL VARS}-> N33
none:	N40 -{Function start dummy edge}-> N14
line 34:	N18 -{f(0)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 16:	N2 -{int *c = &t;}-> N3
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 17:	N3 -{intLock();}-> N4
line 18:	N4 -{*c = 2;}-> N5
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 19:	N5 -{kernDispatchDisable();}-> N6
line 20:	N6 -{*c = 4;}-> N7
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 21:	N7 -{intUnlock();}-> N8
Line 0:     N0 -{highlight}-> N0
line 22:	N8 -{*c = 3;}-> N9
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 23:	N9 -{kernDispatchEnable();}-> N10
line 24:	N10 -{return 0;}-> N0
Line 0:     N0 -{return;}-> N0

##ldv_main
1
int *temp
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N13 -{INIT GLOBAL VARS}-> N33
none:	N40 -{Function start dummy edge}-> N14
line 34:	N18 -{f(0)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 16:	N2 -{int *c = &t;}-> N3
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 17:	N3 -{intLock();}-> N4
line 18:	N4 -{*c = 2;}-> N5
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 19:	N5 -{kernDispatchDisable();}-> N6
line 20:	N6 -{*c = 4;}-> N7
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 21:	N7 -{intUnlock();}-> N8
line 22:	N8 -{*c = 3;}-> N9
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 23:	N9 -{kernDispatchEnable();}-> N10
line 24:	N10 -{return 0;}-> N0
line 34:	N0 -{Return edge from f to ldv_main}-> N19
line 37:	N19 -{q = *temp;}-> N20
line 43:	N21 -{temp2 = sdlFirst(temp);}-> N26
line 44:	N26 -{temp2 = sdlFirst(temp2);}-> N27
Line 0:     N0 -{highlight}-> N0
line 46:	N27 -{*temp2 = 1;}-> N28
Line 0:     N0 -{/*Change states for locks queLOCK(a)*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 49:	N28 -{queLOCK(a);}-> N29
line 50:	N29 -{p = 1;}-> N30
Line 0:     N0 -{/*Change states for locks queLOCK(q)*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 51:	N30 -{queUNLOCK(q);}-> N31
line 52:	N31 -{p = 2;}-> N32
none:	N32 -{default return}-> N12

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N13 -{INIT GLOBAL VARS}-> N33
none:	N40 -{Function start dummy edge}-> N14
line 34:	N18 -{f(0)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 16:	N2 -{int *c = &t;}-> N3
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 17:	N3 -{intLock();}-> N4
line 18:	N4 -{*c = 2;}-> N5
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 19:	N5 -{kernDispatchDisable();}-> N6
line 20:	N6 -{*c = 4;}-> N7
Line 0:     N0 -{/*Change states for locks intLock*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 21:	N7 -{intUnlock();}-> N8
line 22:	N8 -{*c = 3;}-> N9
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 23:	N9 -{kernDispatchEnable();}-> N10
line 24:	N10 -{return 0;}-> N0
line 34:	N0 -{Return edge from f to ldv_main}-> N19
line 37:	N19 -{q = *temp;}-> N20
Line 0:     N0 -{highlight}-> N0
line 43:	N21 -{temp2 = sdlFirst(temp);}-> N26
line 44:	N26 -{temp2 = sdlFirst(temp2);}-> N27
line 46:	N27 -{*temp2 = 1;}-> N28
Line 0:     N0 -{/*Change states for locks queLOCK(a)*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 49:	N28 -{queLOCK(a);}-> N29
line 50:	N29 -{p = 1;}-> N30
Line 0:     N0 -{/*Change states for locks queLOCK(q)*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 51:	N30 -{queUNLOCK(q);}-> N31
line 52:	N31 -{p = 2;}-> N32
none:	N32 -{default return}-> N12

