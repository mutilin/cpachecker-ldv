int a;

void f() {
  int g;
  
  g = &a;
}

int main() {
  int *b;
  int *c;
  int t;
  
  c = &a;
  //b = c;
  //a = 1;
  a = &t;
 // *b = 2;
  f();
}
