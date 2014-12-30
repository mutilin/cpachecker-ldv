//#include <stdio.h>
int false_unsafe;

int f(int a) {
	if (a & 11) {
		kernDispatchDisable();
		//printf("Enters\n");
		false_unsafe = 1;
		kernDispatchDisable();
		return false_unsafe;
	}
		//printf("Do not enter\n");
	return 0;
}

int ldv_main() {
	false_unsafe = 0;
	int b = f(0);
	if (b != 0) {
		kernDispatchDisable();
		false_unsafe = 1;
		kernDispatchDisable();
	}
}
