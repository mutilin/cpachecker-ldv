/* Generated by CIL v. 1.3.7 */
/* print_CIL_Input is true */

#line 69 "/usr/include/assert.h"
extern  __attribute__((__nothrow__, __noreturn__)) void __assert_fail(char const   *__assertion ,
                                                                      char const   *__file ,
                                                                      unsigned int __line ,
                                                                      char const   *__function ) ;
#line 20 "./oomInt-1.c"
int VERDICT_SAFE  ;
#line 21 "./oomInt-1.c"
int CURRENTLY_SAFE  ;
#line 30 "./oomInt-1.c"
int abs_int(int i ) 
{ 

  {
#line 32
  if (i < 0) {
#line 36
    return (- i);
  } else {
#line 38
    return (i);
  }
}
}
#line 40 "./oomInt-1.c"
int p  =    0;
#line 41 "./oomInt-1.c"
void firstFunction(void) 
{ 

  {
  {
#line 43
  p = abs_int(-3);
  }
#line 44
  if (p >= 0) {

  } else {
    {
#line 44
    __assert_fail("p >= 0", "./oomInt-1.c", 44U, "firstFunction");
    }
  }
#line 45
  return;
}
}
#line 47 "./oomInt-1.c"
void main(void) 
{ 

  {
  {
#line 49
  firstFunction();
  }
#line 50
  return;
}
}
