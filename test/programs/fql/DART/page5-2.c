
void abort();

struct foo 
{ 
  int i; 
  char c; 
};

void bar (struct foo *a) 
{
  if (a->c == 0) 
  {
    *((char *)a + sizeof(int)) = 1;

    if (a->c != 0)
    {
      abort();
    }
  }
}

