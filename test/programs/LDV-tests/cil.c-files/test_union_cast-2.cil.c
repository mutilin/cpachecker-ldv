/* Generated by CIL v. 1.3.7 */
/* print_CIL_Input is true */

#line 19 "test_union_cast-2.c"
struct l_struct_2E_X {
   double field0 ;
};
#line 69 "/usr/include/assert.h"
extern  __attribute__((__nothrow__, __noreturn__)) void __assert_fail(char const   *__assertion ,
                                                                      char const   *__file ,
                                                                      unsigned int __line ,
                                                                      char const   *__function ) ;
#line 7 "test_union_cast-2.c"
int VERDICT_SAFE  ;
#line 8 "test_union_cast-2.c"
int CURRENTLY_UNKNOWN  ;
#line 24 "test_union_cast-2.c"
int main(void) 
{ struct l_struct_2E_X llvm_cbe_var ;

  {
#line 32
  llvm_cbe_var.field0 = 0x1.4p+4;
#line 33
  *((unsigned int *)(& llvm_cbe_var.field0)) = 10U;
#line 34
  if (*((unsigned int *)(& llvm_cbe_var.field0)) == 10U) {

  } else {
    {
#line 34
    __assert_fail("*(((unsigned int *)((&llvm_cbe_var.field0)))) == 10u", "test_union_cast-2.c",
                  34U, "main");
    }
  }
#line 36
  return (0);
}
}
