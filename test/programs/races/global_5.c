/* Test for restore annotation 
 * We have several fixes only for this problem: restore annotations worked 
 * incorrectly. Also, this test may be used to check the work of ABM caching:
 * we call function memFree() in function f() and then call it directly.
 */

#line 1 "../test/programs/races/global_5.c"

int gvar;
int lock_name;

void f(void) {
	memFree();
}

void memFree() {
	pthread_mutex_lock(lock_name);
}

void kernDispatchThread() {
	intLock();
}

void ldv_main(void) {
	gvar = 1;
	f();
	gvar = 1;
	kernDispatchThread();
	gvar = 1;
	memFree();
	gvar = 1;
}