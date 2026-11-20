import java.lang.reflect.*;
public class CsvBoundaryTest {
 public static void main(String[] args) throws Exception {
 Class<?> c=Class.forName("logisim.learner.testing.CsvTestVectorLoader");
 Method m=c.getDeclaredMethod("parseNumber",String.class,int.class,int.class,String.class); m.setAccessible(true);
 String[] values={"0","4294967295","0xFFFFFFFF","4294967296","0x100000000","-1","256","255"};
 int[] widths={32,32,32,32,32,32,8,8};
 boolean[] accept={true,true,true,false,false,false,false,true};
 for(int i=0;i<values.length;i++) { boolean ok=true; try {m.invoke(null,values[i],1,widths[i],"input");} catch(InvocationTargetException e) {if(!(e.getCause() instanceof IllegalArgumentException)) throw e; ok=false;} if(ok!=accept[i]) throw new AssertionError(values[i]); }
 System.out.println("PASS: 8 numeric boundary cases");
 }
}
