#include <stdio.h>

struct testStruct {
  int a;
  int b;
} *s;

int t;

struct testStruct *s1, *s2;

int h(int c);

int g(int b)
{
  int *p = &c; 
  return h(*b); 
} 

int f(int a)
{
  int *c = 1;
  intLock();
  *c = l(2);
  kernDispatchDisable();
  *c = 4;
  intUnlock();
  //goto next;
  *c = 3;
  kernDispatchEnable();
  
  if (*c == 1)
  {
    intLock();
  }
  else
  {
    kernDispatchDisable(); 
  }
  if (*c == 1)
  {
    //c = 1;
    l1(2);
  }
  else
  {
    //c = 2;
    l(1);
  }
  /*if (c > 0) {
    //intLock();
    if (c < 3) {
      goto next;
    }// else {
      //intUnlock();
    //}
    return 1;
    //intUnlock();
  }
  switch (a) {
    case 0:
      return 0;
    case 1:
      return g(a - 1);
    case 2:
      return g(a -2);
    case 3:
      return g(a -3);
    default:
      return g(a - 4);
  }
  next:*/
    return 0;
}

int h(int c) {
  int d;
  //intLock();
  d = c + 1;
  return d; 
}

int l(int c) {
  int *p = &c; 
  return *(p + 1);
}

int l1(int c) {
  int *p = &c; 
  l1(*(p + 2));
}

int l2(int c) {
  return l1(c + 1);
}

int main() 
{
  int a;
  int q = 1;
  int* temp;
  int* temp2;
  t = 1;
  intLock();
  l1(3);
  t = 2;
  intUnlock();
  temp = &a;
  q = h(q);
  switch (q) {
    case 0:
      intLock();
      break;
    case 1:
      kernDispatchDisable();
      break;
  }
  temp = &q;
  intLock();
  a = 1;
  intLock();
  t = 2;
  temp = sdlFirst(&t);
  intUnlock();
  *temp = 3;
  
  if (q == 1)
  {
    intLock();
    q = h(1);
    temp = sdlFirst(&(s->a));
    return;
  } else {
    q = l2(1);
  }
  intUnlock();
  temp2 = sdlFirst(temp);
  temp2 = sdlFirst(temp2);
  q = *temp2;
  h(q);
  t1 = a;
  t2 = link(t1);
  t = *t2;
  temp = &a;
  h(a);
  for (int i = 0; i < 10; i++) {
    intLock();
    a = f(4);
    temp = &a;
    intUnlock();
  }
  s->a = 1;
  if (s->a){
    {
    a = 1;
    }
    a = 2;
  }
  int b = a + q;
  intUnlock();
  queLock(q);
  a = 2;
  queUnlock(q);
  a = 3;
  printf("%i\n",a);  
}
