/* Generated by CIL v. 1.3.7 */
/* print_CIL_Input is true */

#line 18 "test_kzalloc-2_BUG.c"
struct A {
   int *a ;
   int *b ;
};
#line 11 "test_kzalloc-2_BUG.c"
int VERDICT_UNSAFE  ;
#line 12 "test_kzalloc-2_BUG.c"
int CURRENTLY_UNSAFE  ;
#line 29
extern int ( /* missing proto */  malloc)() ;
#line 32
extern int ( /* missing proto */  assert)() ;
#line 23 "test_kzalloc-2_BUG.c"
int main(void) 
{ struct A *x ;
  int tmp ;

  {
  {
#line 29
  tmp = malloc(sizeof(struct A ));
#line 29
  x = (struct A *)tmp;
#line 32
  assert((unsigned long )x->a == (unsigned long )((int *)0));
  }
#line 33
  return (0);
}
}
