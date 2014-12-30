int false_unsafe;
int global;

int main() {
    int undef, tmp;
    if (undef) {
		//global = 1;
        intLock();
    }
    //tmp++;
    if (undef) {
		//global = 0;
        intUnlock();
    }
}

int f() {
	/*if (global != 0) {
	ERROR:
	goto ERROR;
}*/
	false_unsafe++;
}

int ldv_main() {
	f();
    main();
    f();
}

