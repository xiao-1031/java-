import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import logisim.learner.testing.*;
public class CsvIntegrationTest {
 static int checks;
 static void check(boolean b) {checks++; if(!b) throw new AssertionError("check "+checks);}
 static TestSuite read(String text, List<TestSignal> signals) throws Exception {
  Path f=Files.createTempFile("csv-test", ".csv");
  try {Files.write(f,text.getBytes(StandardCharsets.UTF_8)); return CsvTestVectorLoader.load(f.toFile(),signals,1,false);} finally {Files.deleteIfExists(f);}
 }
 static void reject(String text,List<TestSignal> signals) throws Exception {
  try {read(text,signals); throw new AssertionError("accepted malformed CSV");} catch(IllegalArgumentException expected) {checks++;}
 }
 public static void main(String[] args) throws Exception {
  Constructor<TestSignal> ctor=TestSignal.class.getDeclaredConstructor(com.cburch.logisim.comp.Component.class,String.class,int.class,boolean.class); ctor.setAccessible(true);
  List<TestSignal> signals=Arrays.asList(ctor.newInstance(null,"a,b",32,true),ctor.newInstance(null,"q",32,false));
  TestSuite suite=read("\ufeff\"a,b\",expected:q,ticks,reset\n0xFFFFFFFF,0,2,true\n",signals);
  check(suite.getVectors().get(0).getInputs().get("a,b")==-1);
  check(suite.getVectors().get(0).getTicks()==2);
  check(suite.getVectors().get(0).isResetBefore());
  Path out=Files.createTempFile("roundtrip", ".csv");
  try {CsvTestVectorWriter.write(out.toFile(),suite); TestSuite back=CsvTestVectorLoader.load(out.toFile(),signals,0,false); check(back.getVectors().get(0).getInputs().equals(suite.getVectors().get(0).getInputs())); check(back.getVectors().get(0).getExpectedOutputs().equals(suite.getVectors().get(0).getExpectedOutputs()));} finally {Files.deleteIfExists(out);}
  reject("\"a,b\"oops,ticks\n1,1\n",signals);
  reject("\"a,b\",ticks\n4294967296,1\n",signals);
  reject("\"a,b\",ticks\n1,-1\n",signals);
  reject("\"a,b\",ticks\n1\n",signals);
  reject("\"a,b\",reset\n1,maybe\n",signals);
  reject("",signals);
  reject("\"a,b\",ticks\n",signals);
  System.out.println("PASS: "+checks+" CSV integration checks");
 }
}
