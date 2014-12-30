int false_unsafe;
int global;
int (*p)();

int main() {
    global++;
    p = &g;
    (*p)();
}

int f() {
	false_unsafe++;
}

int g() {
	global++;
}

int ldv_main() {
	f();
    main();
    f();
}

