struct pthread {
    int tmp;
};

typedef struct pthread pthread_t;

struct ldv_thread {
    int n;
    pthread_t **threads;
};

int safe;
int unsafe;

struct thread *ldv_thread_create(void *(*start_routine) (void *), void *arg) {
    (*start_routine)(arg);
}

int ldv_thread_join(void *(*start_routine) (void *), pthread_t *thread) {
    //??
}

void* control_function(void *arg) {
    f();
}

int f() {
    safe = 1;
    unsafe = 1;
}

int ldv_main() {
    int *a;
	ldv_thread_create(control_function, a);
    unsafe = 0;
    ldv_thread_join(control_function, a);
    safe = 1;
}
