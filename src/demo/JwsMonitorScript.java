/**
 * 
 */
package demo;


import java.lang.reflect.Field;
import static com.sun.btrace.BTraceUtils.*;

import com.sun.btrace.AnyType;
import com.sun.btrace.BTraceUtils;
import com.sun.btrace.BTraceUtils.Strings;
import com.sun.btrace.annotations.BTrace;
import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.Location;
import com.sun.btrace.annotations.OnMethod;
import com.sun.btrace.annotations.ProbeClassName;
import com.sun.btrace.annotations.ProbeMethodName;
import com.sun.btrace.annotations.Self;
import com.sun.btrace.annotations.TargetInstance;
import com.sun.btrace.annotations.TargetMethodOrField;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2014-2-11
 * 
 */
@BTrace(unsafe=false)
public class JwsMonitorScript {
    
    @OnMethod(
            clazz = "jws.server.JwsHandler",
            method = "messageReceived",
            location = @Location(value = Kind.ENTRY))
    public static void execute(){
        println("=======execute===========");
        //jstack();
    }
    @OnMethod(
            clazz = "jws.server.JwsHandler",
            method = "messageReceived",
            location = @Location(value = Kind.ENTRY))
    public static void getParams(){
        println("=======get Params===========");
        //printArray(args);
        /*Field reqMethodField = field("jws.mvc.Http.Request","method");
        Field reqUrlField = field("jws.mvc.Http.Request","url");
        Object reqMethod = BTraceUtils.get(reqMethodField,thisObject);
        Object reqUrl = BTraceUtils.get(reqUrlField,thisObject);
        BTraceUtils.println(Strings.concat("reqMethod=", Strings.str(reqMethod)));
        BTraceUtils.println(Strings.concat("reqUrl=", Strings.str(reqUrl)));*/
    }
    
   @OnMethod(
            clazz = "jws.server.JwsHandler",
           method = "messageReceived",
           location = @Location(value = Kind.CALL, clazz="/.*/", method="/.*/"))
    public static void methodCall(@ProbeClassName String pcn, @ProbeMethodName String pmn,
            @TargetInstance Object obj,@TargetMethodOrField Object f){
        //println("=======methodCall===========");
        //println(Strings.concat("@ProbeClassName=", pcn));
       // println(Strings.concat("@ProbeMethodName=", pmn));
      //  println(Strings.concat("@TargetInstance=", Strings.str(obj)));
      //  println(Strings.concat("@TargetMethodOrField=", Strings.str(f)));
       // println(Strings.concat("text=", text));
    }
    
  
    @OnMethod(
            clazz = "jws.server.JwsHandler",
            method = "serve500",
            location = @Location(value = Kind.THROW))
    public static void getThrow(Throwable e){
        println("=====serve500 getThrow========");
        jstack(e);
    }
    
    
    
    @OnMethod(
            clazz = "jws.server.JwsHandler",
            method = "messageReceived",
            location = @Location(value = Kind.CATCH))
    public static void doCatch(Exception e){
       println("=====messageReceived catch========");
        BTraceUtils.jstack(e);
    }
    
    @OnMethod(
            clazz = "jws.server.JwsHandler",
            method = "serve500",
            location = @Location(value = Kind.CATCH))
    public static void doCatch2(Throwable e){
       println("=====serve500 catch========");
        BTraceUtils.jstack(e);
    }
   
}
