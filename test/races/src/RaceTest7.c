int false_unsafe;
int threadDispatchLevel;
int false_unsafe2;
int true_unsafe;

int f() {
	if (threadDispatchLevel) {
		false_unsafe = 0;
	} else {
		kernDispatchDisable();
		false_unsafe2 = 1;
		true_unsafe = 0;
		kernDispatchEnable();
	}
}

int g() {
	kernDispatchDisable();
	false_unsafe = 1;
	false_unsafe2 = 1;
	kernDispatchEnable();
	true_unsafe = 0;
}

int ldv_main() {
	f();
	g();
}
