/* Generated by CIL v. 1.3.7 */
/* print_CIL_Input is true */

#line 211 "/usr/lib64/gcc/x86_64-suse-linux/4.5/include/stddef.h"
typedef unsigned long size_t;
#line 180 "/usr/include/bits/types.h"
typedef long __ssize_t;
#line 110 "/usr/include/sys/types.h"
typedef __ssize_t ssize_t;
#line 471 "/usr/include/stdlib.h"
extern  __attribute__((__nothrow__)) void *malloc(size_t __size )  __attribute__((__malloc__)) ;
#line 69 "/usr/include/assert.h"
extern  __attribute__((__nothrow__, __noreturn__)) void __assert_fail(char const   *__assertion ,
                                                                      char const   *__file ,
                                                                      unsigned int __line ,
                                                                      char const   *__function ) ;
#line 85 "/usr/include/fcntl.h"
extern int open(char const   *__file , int __oflag  , ...)  __attribute__((__nonnull__(1))) ;
#line 7 "./fo_test.c"
int VERDICT_UNSAFE  ;
#line 8 "./fo_test.c"
int CURRENTLY_UNSAFE  ;
#line 10 "./fo_test.c"
int globalState  =    0;
#line 11
ssize_t l_read(int fd , char *cbuf , size_t count ) ;
#line 12
int l_open(char *file , int flags ) ;
#line 14 "./fo_test.c"
int main(int argc , char **argv ) 
{ int file ;
  int tmp ;
  void *cbuf ;
  void *tmp___0 ;
  int a ;
  ssize_t tmp___1 ;

  {
  {
#line 16
  tmp = l_open((char *)"unknown", 0);
#line 16
  file = tmp;
#line 17
  tmp___0 = malloc(sizeof(char ) * 100UL);
#line 17
  cbuf = tmp___0;
#line 18
  tmp___1 = l_read(file, (char *)cbuf, 99UL);
#line 18
  a = (int )tmp___1;
  }
#line 19
  return (0);
}
}
#line 24
extern int ( /* missing proto */  read)() ;
#line 22 "./fo_test.c"
ssize_t l_read(int fd , char *cbuf , size_t count ) 
{ int tmp ;

  {
#line 23
  if (globalState == 1) {

  } else {
    {
#line 23
    __assert_fail("globalState == 1", "./fo_test.c", 23U, "l_read");
    }
  }
  {
#line 24
  tmp = read(fd, cbuf, count);
  }
#line 24
  return ((long )tmp);
}
}
#line 27 "./fo_test.c"
int l_open(char *file , int flags ) 
{ int fd ;
  int tmp ;

  {
  {
#line 28
  tmp = open((char const   *)file, flags);
#line 28
  fd = tmp;
  }
#line 29
  if (fd > 0) {
#line 29
    globalState = 1;
  } else {

  }
#line 30
  return (fd);
}
}
