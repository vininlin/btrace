/**
 * 
 */
package demo;



import static com.sun.btrace.BTraceUtils.*;


import com.sun.btrace.AnyType;
import com.sun.btrace.BTraceUtils;
import com.sun.btrace.annotations.BTrace;
import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.Location;
import com.sun.btrace.annotations.OnMethod;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2014-2-11
 * 
 */
@BTrace(unsafe=false)
public class JwsBtraceScript {
    
    @OnMethod(
            clazz = "jws.server.JwsHandler",
            method = "serve500",
            location = @Location(value = Kind.ENTRY))
    public static void getParams(AnyType[] args){
        println("=======serve500 get Params===========");
        printArray(args);
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
