/* Test for several annotated functions spl*
 * (There are some suspicions, that there work wrong way)
 */

static pthread_mutex_t ipl_mutex  ;
int gvar;

__inline static int spl(void) 
{ 
  int tmp ;
  tmp = pthread_mutex_lock(& ipl_mutex);
  return (tmp);
}

int splbio(void) 
{
  int tmp ;
  tmp = spl();
  return (tmp);
}

void splx(int x ) 
{ 
  if (x == 0) {
  pthread_mutex_unlock(& ipl_mutex);
  return;
}
}


void ldv_main(void)
{
  int t = splbio();
  gvar = 0;
  splx(t);
  gvar = 1;
  return;
}