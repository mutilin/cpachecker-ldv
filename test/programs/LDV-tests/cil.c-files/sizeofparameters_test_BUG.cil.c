/* Generated by CIL v. 1.3.7 */
/* print_CIL_Input is true */

#line 4 "sizeofparameters_test.c"
int VERDICT_UNSAFE  ;
#line 5 "sizeofparameters_test.c"
int CURRENTLY_UNSAFE  ;
#line 7
void foo(int a ) ;
#line 9 "sizeofparameters_test.c"
int globalSize  ;
#line 11 "sizeofparameters_test.c"
int main(int argc , char **argv ) 
{ long a ;

  {
  {
#line 14
  globalSize = (int )sizeof(a);
#line 15
  foo(a);
  }
#line 16
  return (0);
}
}
#line 20
extern int ( /* missing proto */  assert)() ;
#line 19 "sizeofparameters_test.c"
void foo(int a ) 
{ 

  {
  {
#line 20
  assert(sizeof(a) == (unsigned long )globalSize);
  }
#line 21
  return;
}
}
