
public class IfStatement5 {




  public static void main(
      String[] args) {


            boolean a1 = true;
            boolean a2 = false;
            boolean a3 = false;
            boolean a4 = true;
            boolean a5 =  (a4 && a1) || a3;


            if(((a1 && !a2) || !(a3 && a4)) && a5){


            }else {

            assert false ;

            }
      }
}