struct point {
  int *x;
  int y;
} *A;

struct line {
  point* start;
  point* finish;
} *L1;

int t;
int *G;
int **p;

int *f(int *m) {
  //m = &t;
  point *C;
  *m = 10;
  C->x = a;
  C->y = 1;
  return C->x;
}

point* getStart(struct line *l) {
  if ( l != null) {
    //f(G);
    l->start = &t;
    return l->start;
  } else {
    //f(*p);
    return null;
  }
}

int main() {
  int *a;//, *c;
 // int b;
  struct point B;
  //struct line *L2;
  
  /*a = &b;
  c = a;
  a = malloc();*/
  A = getStart(&B);
  //L2->start = B;
  /*p = &c;
  *p = a;
  c = &b;*/
  B->x = f(a);
}