/*
	error path programm generated by CPAchecker

	base program:	test/programs/ssh-simplified/s3_clnt_3_BUG.cil.c
	config file:	test/config/predicateAnalysis.properties
	
	remove line 170 in order to make the error path feasible using cbmc
*/

int main()
{
int s ;
s = 12292;
int initial_state;
initial_state = s;
int s__info_callback ;
int s__in_handshake ;
int s__state ;
int s__new_session ;
int s__server ;
int s__version ;
int s__type ;
int s__init_num ;
int s__bbio ;
int s__wbio ;
int s__hit ;
int s__rwstate ;
int s__init_buf___0 ;
int s__debug ;
int s__shutdown ;
int s__ctx__info_callback ;
int s__ctx__stats__sess_connect_renegotiate ;
int s__ctx__stats__sess_connect ;
int s__ctx__stats__sess_hit ;
int s__ctx__stats__sess_connect_good ;
int s__s3__change_cipher_spec ;
int s__s3__flags ;
int s__s3__delay_buf_pop_ret ;
int s__s3__tmp__cert_req ;
int s__s3__tmp__new_compression ;
int s__s3__tmp__reuse_message ;
int s__s3__tmp__new_cipher ;
int s__s3__tmp__new_cipher__algorithms ;
int s__s3__tmp__next_state___0 ;
int s__s3__tmp__new_compression__id ;
int s__session__cipher ;
int s__session__compress_meth ;
int buf ;
unsigned long tmp ;
unsigned long l ;
int num1 ;
int cb ;
int ret ;
int new_state ;
int state ;
int skip ;
int tmp___0 ;
int tmp___1 ;
int tmp___2 ;
int tmp___3 ;
int tmp___4 ;
int tmp___5 ;
int tmp___6 ;
int tmp___7 ;
int tmp___8 ;
int tmp___9 ;
int __BLAST_NONDET ;
int blastFlag ;
int __cil_tmp55 ;
void *__cil_tmp56 ;
unsigned long __cil_tmp57 ;
unsigned long __cil_tmp58 ;
void *__cil_tmp59 ;
unsigned long __cil_tmp60 ;
unsigned long __cil_tmp61 ;
unsigned long __cil_tmp62 ;
unsigned long __cil_tmp63 ;
unsigned long __cil_tmp64 ;
long __cil_tmp65 ;
long __cil_tmp66 ;
long __cil_tmp67 ;
long __cil_tmp68 ;
long __cil_tmp69 ;
long __cil_tmp70 ;
long __cil_tmp71 ;
long __cil_tmp72 ;
long __cil_tmp73 ;
long __cil_tmp74 ;
__BLAST_NONDET = random();
s__state = initial_state;
blastFlag = 0;
tmp = (unsigned long )__BLAST_NONDET;
cb = 0;
ret = -1;
skip = 0;
tmp___0 = 0;
__CPROVER_assume(s__info_callback != 0);
cb = s__info_callback;
s__in_handshake ++;
__CPROVER_assume(!(tmp___1 + 12288));
state = s__state;
__CPROVER_assume(s__state == 12292);
s__new_session = 1;
s__state = 4096;
s__ctx__stats__sess_connect_renegotiate ++;
s__server = 0;
__CPROVER_assume(cb != 0);
__cil_tmp55 = s__version + 65280;
__CPROVER_assume(!(__cil_tmp55 != 768));
s__type = 4096;
__cil_tmp56 = (void *)0;
__cil_tmp57 = (unsigned long )__cil_tmp56;
__cil_tmp58 = (unsigned long )s__init_buf___0;
__CPROVER_assume(!(__cil_tmp58 == __cil_tmp57));
__CPROVER_assume(!(! tmp___4));
__CPROVER_assume(!(! tmp___5));
s__state = 4368;
s__ctx__stats__sess_connect ++;
s__init_num = 0;
__CPROVER_assume(!(! s__s3__tmp__reuse_message));
skip = 0;
state = s__state;
__CPROVER_assume(!(s__state == 12292));
__CPROVER_assume(!(s__state == 16384));
__CPROVER_assume(!(s__state == 4096));
__CPROVER_assume(!(s__state == 20480));
__CPROVER_assume(!(s__state == 4099));
__CPROVER_assume(s__state == 4368);
s__shutdown = 0;
ret = __BLAST_NONDET;
__CPROVER_assume(blastFlag == 0);
blastFlag = 1;
__CPROVER_assume(!(ret <= 0));
s__state = 4384;
s__init_num = 0;
__cil_tmp62 = (unsigned long )s__wbio;
__cil_tmp63 = (unsigned long )s__bbio;
__CPROVER_assume(__cil_tmp63 != __cil_tmp62);
__CPROVER_assume(!(! s__s3__tmp__reuse_message));
skip = 0;
state = s__state;
__CPROVER_assume(!(s__state == 12292));
__CPROVER_assume(!(s__state == 16384));
__CPROVER_assume(!(s__state == 4096));
__CPROVER_assume(!(s__state == 20480));
__CPROVER_assume(!(s__state == 4099));
__CPROVER_assume(!(s__state == 4368));
__CPROVER_assume(!(s__state == 4369));
__CPROVER_assume(s__state == 4384);
ret = __BLAST_NONDET;
__CPROVER_assume(blastFlag == 1);
blastFlag = 2;
__CPROVER_assume(!(ret <= 0));
__CPROVER_assume(!(s__hit));
s__state = 4400;
s__init_num = 0;
__CPROVER_assume(!(! s__s3__tmp__reuse_message));
skip = 0;
state = s__state;
__CPROVER_assume(!(s__state == 12292));
__CPROVER_assume(!(s__state == 16384));
__CPROVER_assume(!(s__state == 4096));
__CPROVER_assume(!(s__state == 20480));
__CPROVER_assume(!(s__state == 4099));
__CPROVER_assume(!(s__state == 4368));
__CPROVER_assume(!(s__state == 4369));
__CPROVER_assume(!(s__state == 4384));
__CPROVER_assume(!(s__state == 4385));
__CPROVER_assume(s__state == 4400);
__cil_tmp64 = (unsigned long )s__s3__tmp__new_cipher__algorithms;
__CPROVER_assume(!(__cil_tmp64 + 256UL));
ret = __BLAST_NONDET;
__CPROVER_assume(blastFlag == 2);
blastFlag = 3;
__CPROVER_assume(!(ret <= 0));
s__state = 4416;
s__init_num = 0;
__CPROVER_assume(!(! s__s3__tmp__reuse_message));
skip = 0;
state = s__state;
__CPROVER_assume(!(s__state == 12292));
__CPROVER_assume(!(s__state == 16384));
__CPROVER_assume(!(s__state == 4096));
__CPROVER_assume(!(s__state == 20480));
__CPROVER_assume(!(s__state == 4099));
__CPROVER_assume(!(s__state == 4368));
__CPROVER_assume(!(s__state == 4369));
__CPROVER_assume(!(s__state == 4384));
__CPROVER_assume(!(s__state == 4385));
__CPROVER_assume(!(s__state == 4400));
__CPROVER_assume(!(s__state == 4401));
__CPROVER_assume(s__state == 4416);
ret = __BLAST_NONDET;
__CPROVER_assume(blastFlag == 3);
blastFlag = 4;
__CPROVER_assume(!(ret <= 0));
s__state = 4432;
s__init_num = 0;
__CPROVER_assume(!(! tmp___6));
__CPROVER_assume(!(! s__s3__tmp__reuse_message));
skip = 0;
state = s__state;
__CPROVER_assume(!(s__state == 12292));
__CPROVER_assume(!(s__state == 16384));
__CPROVER_assume(!(s__state == 4096));
__CPROVER_assume(!(s__state == 20480));
__CPROVER_assume(!(s__state == 4099));
__CPROVER_assume(!(s__state == 4368));
__CPROVER_assume(!(s__state == 4369));
__CPROVER_assume(!(s__state == 4384));
__CPROVER_assume(!(s__state == 4385));
__CPROVER_assume(!(s__state == 4400));
__CPROVER_assume(!(s__state == 4401));
__CPROVER_assume(!(s__state == 4416));
__CPROVER_assume(!(s__state == 4417));
__CPROVER_assume(s__state == 4432);
ret = __BLAST_NONDET;
__CPROVER_assume(blastFlag == 4);
goto ERROR;
ERROR:
return (-1);
}
