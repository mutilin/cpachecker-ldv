3
0
0
0
0
0
3
#
0
int global
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:1*/}-> N0
Line 0:     N0 -{/*Number of usages      :1*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N96 -{INIT GLOBAL VARS}-> N99
none:	N113 -{Function start dummy edge}-> N97
line 104:	N97 -{main(0)}-> N90
none:	N90 -{Function start dummy edge}-> N91
line 98:	N91 -{difficult_function()}-> N56
none:	N56 -{Function start dummy edge}-> N57
line 64:	N57 -{int ret;}-> N58
line 64:	N58 -{int param;}-> N59
line 64:	N59 -{int mutex;}-> N60
line 65:	N60 -{get(mutex)}-> N14
none:	N14 -{Function start dummy edge}-> N15
line 21:	N15 -{int rt;}-> N16
line 21:	N16 -{int mtx;}-> N17
line 21:	N17 -{int tmp___1;}-> N18
line 23:	N18 -{[!(mutex == 0)]}-> N19
line 26:	N19 -{tryLock(rt)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 7:	N2 -{int idx;}-> N3
line 8:	N3 -{[!(id___0 == 0)]}-> N4
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 11:	N4 -{kernDispatchDisable();}-> N7
line 12:	N7 -{[!(id___0 == idx)]}-> N8
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 15:	N8 -{kernDispatchEnable();}-> N11
line 16:	N11 -{return 0;}-> N0
line 26:	N0 -{Return edge from tryLock to get}-> N22
line 27:	N22 -{[!(mtx != 0)]}-> N23
line 30:	N23 -{init(mutex)}-> N40
none:	N40 -{Function start dummy edge}-> N41
line 48:	N41 -{int mtx;}-> N42
line 48:	N42 -{int rt;}-> N43
line 50:	N43 -{[mutex == 2315255808U]}-> N45
line 51:	N45 -{[rt == 0]}-> N46
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 54:	N46 -{kernDispatchDisable();}-> N49
line 55:	N49 -{[mtx != 0]}-> N51
line 56:	N51 -{return mtx;}-> N39
line 30:	N39 -{Return edge from init to get}-> N26
line 31:	N26 -{return tmp___1;}-> N13
line 65:	N13 -{Return edge from get to difficult_function}-> N61
line 66:	N61 -{[!(ret == 0)]}-> N62
lines 69-70:	N62 -{Label: restart}-> N65
line 70:	N65 -{false_unsafe = 0;}-> N66
line 71:	N66 -{true_unsafe = 0;}-> N67
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 73:	N67 -{kernDispatchEnable();}-> N68
line 74:	N68 -{check(param)}-> N29
none:	N29 -{Function start dummy edge}-> N30
line 36:	N30 -{int tmp;}-> N31
line 37:	N31 -{[!(code == 27)]}-> N32
line 43:	N32 -{return code;}-> N28
line 74:	N28 -{Return edge from check to difficult_function}-> N69
line 75:	N69 -{[!(ret == 27)]}-> N70
line 78:	N70 -{unsafe = 1;}-> N73
line 79:	N73 -{true_unsafe = 0;}-> N74
none:	N74 -{default return}-> N55
line 98:	N55 -{Return edge from difficult_function to main}-> N92
line 99:	N92 -{g()}-> N86
none:	N86 -{Function start dummy edge}-> N87
Line 0:     N0 -{highlight}-> N0
line 94:	N87 -{global = 1;}-> N88
none:	N88 -{default return}-> N85
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N96 -{INIT GLOBAL VARS}-> N99
none:	N113 -{Function start dummy edge}-> N97
line 104:	N97 -{main(0)}-> N90
none:	N90 -{Function start dummy edge}-> N91
line 98:	N91 -{difficult_function()}-> N56
none:	N56 -{Function start dummy edge}-> N57
line 64:	N57 -{int ret;}-> N58
line 64:	N58 -{int param;}-> N59
line 64:	N59 -{int mutex;}-> N60
line 65:	N60 -{get(mutex)}-> N14
none:	N14 -{Function start dummy edge}-> N15
line 21:	N15 -{int rt;}-> N16
line 21:	N16 -{int mtx;}-> N17
line 21:	N17 -{int tmp___1;}-> N18
line 23:	N18 -{[!(mutex == 0)]}-> N19
line 26:	N19 -{tryLock(rt)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 7:	N2 -{int idx;}-> N3
line 8:	N3 -{[!(id___0 == 0)]}-> N4
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 11:	N4 -{kernDispatchDisable();}-> N7
line 12:	N7 -{[!(id___0 == idx)]}-> N8
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 15:	N8 -{kernDispatchEnable();}-> N11
line 16:	N11 -{return 0;}-> N0
line 26:	N0 -{Return edge from tryLock to get}-> N22
line 27:	N22 -{[!(mtx != 0)]}-> N23
line 30:	N23 -{init(mutex)}-> N40
none:	N40 -{Function start dummy edge}-> N41
line 48:	N41 -{int mtx;}-> N42
line 48:	N42 -{int rt;}-> N43
line 50:	N43 -{[mutex == 2315255808U]}-> N45
line 51:	N45 -{[rt == 0]}-> N46
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 54:	N46 -{kernDispatchDisable();}-> N49
line 55:	N49 -{[mtx != 0]}-> N51
line 56:	N51 -{return mtx;}-> N39
line 30:	N39 -{Return edge from init to get}-> N26
line 31:	N26 -{return tmp___1;}-> N13
line 65:	N13 -{Return edge from get to difficult_function}-> N61
line 66:	N61 -{[!(ret == 0)]}-> N62
lines 69-70:	N62 -{Label: restart}-> N65
line 70:	N65 -{false_unsafe = 0;}-> N66
line 71:	N66 -{true_unsafe = 0;}-> N67
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 73:	N67 -{kernDispatchEnable();}-> N68
line 74:	N68 -{check(param)}-> N29
none:	N29 -{Function start dummy edge}-> N30
line 36:	N30 -{int tmp;}-> N31
line 37:	N31 -{[!(code == 27)]}-> N32
line 43:	N32 -{return code;}-> N28
line 74:	N28 -{Return edge from check to difficult_function}-> N69
line 75:	N69 -{[!(ret == 27)]}-> N70
line 78:	N70 -{unsafe = 1;}-> N73
line 79:	N73 -{true_unsafe = 0;}-> N74
none:	N74 -{default return}-> N55
line 98:	N55 -{Return edge from difficult_function to main}-> N92
line 99:	N92 -{g()}-> N86
none:	N86 -{Function start dummy edge}-> N87
Line 0:     N0 -{highlight}-> N0
line 94:	N87 -{global = 1;}-> N88
none:	N88 -{default return}-> N85
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

#
0
int true_unsafe
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:2*/}-> N0
Line 0:     N0 -{/*Number of usages      :2*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N96 -{INIT GLOBAL VARS}-> N99
none:	N113 -{Function start dummy edge}-> N97
line 104:	N97 -{main(0)}-> N90
none:	N90 -{Function start dummy edge}-> N91
line 98:	N91 -{difficult_function()}-> N56
none:	N56 -{Function start dummy edge}-> N57
line 64:	N57 -{int ret;}-> N58
line 64:	N58 -{int param;}-> N59
line 64:	N59 -{int mutex;}-> N60
line 65:	N60 -{get(mutex)}-> N14
none:	N14 -{Function start dummy edge}-> N15
line 21:	N15 -{int rt;}-> N16
line 21:	N16 -{int mtx;}-> N17
line 21:	N17 -{int tmp___1;}-> N18
line 23:	N18 -{[!(mutex == 0)]}-> N19
line 26:	N19 -{tryLock(rt)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 7:	N2 -{int idx;}-> N3
line 8:	N3 -{[!(id___0 == 0)]}-> N4
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 11:	N4 -{kernDispatchDisable();}-> N7
line 12:	N7 -{[!(id___0 == idx)]}-> N8
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 15:	N8 -{kernDispatchEnable();}-> N11
line 16:	N11 -{return 0;}-> N0
line 26:	N0 -{Return edge from tryLock to get}-> N22
line 27:	N22 -{[!(mtx != 0)]}-> N23
line 30:	N23 -{init(mutex)}-> N40
none:	N40 -{Function start dummy edge}-> N41
line 48:	N41 -{int mtx;}-> N42
line 48:	N42 -{int rt;}-> N43
line 50:	N43 -{[mutex == 2315255808U]}-> N45
line 51:	N45 -{[rt == 0]}-> N46
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 54:	N46 -{kernDispatchDisable();}-> N49
line 55:	N49 -{[mtx != 0]}-> N51
line 56:	N51 -{return mtx;}-> N39
line 30:	N39 -{Return edge from init to get}-> N26
line 31:	N26 -{return tmp___1;}-> N13
line 65:	N13 -{Return edge from get to difficult_function}-> N61
line 66:	N61 -{[!(ret == 0)]}-> N62
lines 69-70:	N62 -{Label: restart}-> N65
line 70:	N65 -{false_unsafe = 0;}-> N66
line 71:	N66 -{true_unsafe = 0;}-> N67
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 73:	N67 -{kernDispatchEnable();}-> N68
line 74:	N68 -{check(param)}-> N29
none:	N29 -{Function start dummy edge}-> N30
line 36:	N30 -{int tmp;}-> N31
line 37:	N31 -{[!(code == 27)]}-> N32
line 43:	N32 -{return code;}-> N28
line 74:	N28 -{Return edge from check to difficult_function}-> N69
line 75:	N69 -{[!(ret == 27)]}-> N70
line 78:	N70 -{unsafe = 1;}-> N73
Line 0:     N0 -{highlight}-> N0
line 79:	N73 -{true_unsafe = 0;}-> N74
none:	N74 -{default return}-> N55
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*kernDispatchDisable[1]*/}-> N0
none:	N96 -{INIT GLOBAL VARS}-> N99
none:	N113 -{Function start dummy edge}-> N97
line 104:	N97 -{main(0)}-> N90
none:	N90 -{Function start dummy edge}-> N91
line 98:	N91 -{difficult_function()}-> N56
none:	N56 -{Function start dummy edge}-> N57
line 64:	N57 -{int ret;}-> N58
line 64:	N58 -{int param;}-> N59
line 64:	N59 -{int mutex;}-> N60
line 65:	N60 -{get(mutex)}-> N14
none:	N14 -{Function start dummy edge}-> N15
line 21:	N15 -{int rt;}-> N16
line 21:	N16 -{int mtx;}-> N17
line 21:	N17 -{int tmp___1;}-> N18
line 23:	N18 -{[!(mutex == 0)]}-> N19
line 26:	N19 -{tryLock(rt)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 7:	N2 -{int idx;}-> N3
line 8:	N3 -{[!(id___0 == 0)]}-> N4
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 11:	N4 -{kernDispatchDisable();}-> N7
line 12:	N7 -{[!(id___0 == idx)]}-> N8
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 15:	N8 -{kernDispatchEnable();}-> N11
line 16:	N11 -{return 0;}-> N0
line 26:	N0 -{Return edge from tryLock to get}-> N22
line 27:	N22 -{[!(mtx != 0)]}-> N23
line 30:	N23 -{init(mutex)}-> N40
none:	N40 -{Function start dummy edge}-> N41
line 48:	N41 -{int mtx;}-> N42
line 48:	N42 -{int rt;}-> N43
line 50:	N43 -{[mutex == 2315255808U]}-> N45
line 51:	N45 -{[rt == 0]}-> N46
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 54:	N46 -{kernDispatchDisable();}-> N49
line 55:	N49 -{[mtx != 0]}-> N51
line 56:	N51 -{return mtx;}-> N39
line 30:	N39 -{Return edge from init to get}-> N26
line 31:	N26 -{return tmp___1;}-> N13
line 65:	N13 -{Return edge from get to difficult_function}-> N61
line 66:	N61 -{[!(ret == 0)]}-> N62
lines 69-70:	N62 -{Label: restart}-> N65
line 70:	N65 -{false_unsafe = 0;}-> N66
Line 0:     N0 -{highlight}-> N0
line 71:	N66 -{true_unsafe = 0;}-> N67
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 73:	N67 -{kernDispatchEnable();}-> N68
line 74:	N68 -{check(param)}-> N29
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

#
0
int unsafe
Line 0:     N0 -{/*Is true unsafe:*/}-> N0
Line 0:     N0 -{/*Number of usage points:1*/}-> N0
Line 0:     N0 -{/*Number of usages      :1*/}-> N0
Line 0:     N0 -{/*Two examples:*/}-> N0
Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N96 -{INIT GLOBAL VARS}-> N99
none:	N113 -{Function start dummy edge}-> N97
line 104:	N97 -{main(0)}-> N90
none:	N90 -{Function start dummy edge}-> N91
line 98:	N91 -{difficult_function()}-> N56
none:	N56 -{Function start dummy edge}-> N57
line 64:	N57 -{int ret;}-> N58
line 64:	N58 -{int param;}-> N59
line 64:	N59 -{int mutex;}-> N60
line 65:	N60 -{get(mutex)}-> N14
none:	N14 -{Function start dummy edge}-> N15
line 21:	N15 -{int rt;}-> N16
line 21:	N16 -{int mtx;}-> N17
line 21:	N17 -{int tmp___1;}-> N18
line 23:	N18 -{[!(mutex == 0)]}-> N19
line 26:	N19 -{tryLock(rt)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 7:	N2 -{int idx;}-> N3
line 8:	N3 -{[!(id___0 == 0)]}-> N4
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 11:	N4 -{kernDispatchDisable();}-> N7
line 12:	N7 -{[!(id___0 == idx)]}-> N8
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 15:	N8 -{kernDispatchEnable();}-> N11
line 16:	N11 -{return 0;}-> N0
line 26:	N0 -{Return edge from tryLock to get}-> N22
line 27:	N22 -{[!(mtx != 0)]}-> N23
line 30:	N23 -{init(mutex)}-> N40
none:	N40 -{Function start dummy edge}-> N41
line 48:	N41 -{int mtx;}-> N42
line 48:	N42 -{int rt;}-> N43
line 50:	N43 -{[mutex == 2315255808U]}-> N45
line 51:	N45 -{[rt == 0]}-> N46
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 54:	N46 -{kernDispatchDisable();}-> N49
line 55:	N49 -{[mtx != 0]}-> N51
line 56:	N51 -{return mtx;}-> N39
line 30:	N39 -{Return edge from init to get}-> N26
line 31:	N26 -{return tmp___1;}-> N13
line 65:	N13 -{Return edge from get to difficult_function}-> N61
line 66:	N61 -{[!(ret == 0)]}-> N62
lines 69-70:	N62 -{Label: restart}-> N65
line 70:	N65 -{false_unsafe = 0;}-> N66
line 71:	N66 -{true_unsafe = 0;}-> N67
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 73:	N67 -{kernDispatchEnable();}-> N68
line 74:	N68 -{check(param)}-> N29
none:	N29 -{Function start dummy edge}-> N30
line 36:	N30 -{int tmp;}-> N31
line 37:	N31 -{[!(code == 27)]}-> N32
line 43:	N32 -{return code;}-> N28
line 74:	N28 -{Return edge from check to difficult_function}-> N69
line 75:	N69 -{[!(ret == 27)]}-> N70
Line 0:     N0 -{highlight}-> N0
line 78:	N70 -{unsafe = 1;}-> N73
line 79:	N73 -{true_unsafe = 0;}-> N74
none:	N74 -{default return}-> N55
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

Line 0:     N0 -{/*_____________________*/}-> N0
Line 0:     N0 -{/*Without locks*/}-> N0
none:	N96 -{INIT GLOBAL VARS}-> N99
none:	N113 -{Function start dummy edge}-> N97
line 104:	N97 -{main(0)}-> N90
none:	N90 -{Function start dummy edge}-> N91
line 98:	N91 -{difficult_function()}-> N56
none:	N56 -{Function start dummy edge}-> N57
line 64:	N57 -{int ret;}-> N58
line 64:	N58 -{int param;}-> N59
line 64:	N59 -{int mutex;}-> N60
line 65:	N60 -{get(mutex)}-> N14
none:	N14 -{Function start dummy edge}-> N15
line 21:	N15 -{int rt;}-> N16
line 21:	N16 -{int mtx;}-> N17
line 21:	N17 -{int tmp___1;}-> N18
line 23:	N18 -{[!(mutex == 0)]}-> N19
line 26:	N19 -{tryLock(rt)}-> N1
none:	N1 -{Function start dummy edge}-> N2
line 7:	N2 -{int idx;}-> N3
line 8:	N3 -{[!(id___0 == 0)]}-> N4
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 11:	N4 -{kernDispatchDisable();}-> N7
line 12:	N7 -{[!(id___0 == idx)]}-> N8
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 15:	N8 -{kernDispatchEnable();}-> N11
line 16:	N11 -{return 0;}-> N0
line 26:	N0 -{Return edge from tryLock to get}-> N22
line 27:	N22 -{[!(mtx != 0)]}-> N23
line 30:	N23 -{init(mutex)}-> N40
none:	N40 -{Function start dummy edge}-> N41
line 48:	N41 -{int mtx;}-> N42
line 48:	N42 -{int rt;}-> N43
line 50:	N43 -{[mutex == 2315255808U]}-> N45
line 51:	N45 -{[rt == 0]}-> N46
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 54:	N46 -{kernDispatchDisable();}-> N49
line 55:	N49 -{[mtx != 0]}-> N51
line 56:	N51 -{return mtx;}-> N39
line 30:	N39 -{Return edge from init to get}-> N26
line 31:	N26 -{return tmp___1;}-> N13
line 65:	N13 -{Return edge from get to difficult_function}-> N61
line 66:	N61 -{[!(ret == 0)]}-> N62
lines 69-70:	N62 -{Label: restart}-> N65
line 70:	N65 -{false_unsafe = 0;}-> N66
line 71:	N66 -{true_unsafe = 0;}-> N67
Line 0:     N0 -{/*Change states for locks kernDispatchDisable*/}-> N0
Line 0:     N0 -{highlight}-> N0
line 73:	N67 -{kernDispatchEnable();}-> N68
line 74:	N68 -{check(param)}-> N29
none:	N29 -{Function start dummy edge}-> N30
line 36:	N30 -{int tmp;}-> N31
line 37:	N31 -{[!(code == 27)]}-> N32
line 43:	N32 -{return code;}-> N28
line 74:	N28 -{Return edge from check to difficult_function}-> N69
line 75:	N69 -{[!(ret == 27)]}-> N70
Line 0:     N0 -{highlight}-> N0
line 78:	N70 -{unsafe = 1;}-> N73
line 79:	N73 -{true_unsafe = 0;}-> N74
none:	N74 -{default return}-> N55
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0
Line 0:     N0 -{return;}-> N0

