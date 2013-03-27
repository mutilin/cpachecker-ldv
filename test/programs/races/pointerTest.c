struct point {
  int a;
  int b;
};

int global;

int* f() {
  return &global;
}

int* memAlloc() {
  int *c;
  c = h();
  return c;
}
 
int ldv_main() {
  int *a;
  /*struct point *A;
  A->x = 1;
  A->y = 2;*/
  a = f();
  a = memAlloc();
  *a = 1;
}