/* Test checks, how the tool handle bitwise axioms*/
int false_unsafe;

int f(int a) {
	if (a & 11) {
		false_unsafe = 1;
		return false_unsafe;
	}
	return 0;
}

int ldv_main() {
	int b = f(0);
	if (b != 0) {
		false_unsafe = 1;
	}
}
