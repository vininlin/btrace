/**
 * 
 */
package demo;
import static com.sun.btrace.BTraceUtils.*;

import com.sun.btrace.AnyType;
import com.sun.btrace.BTraceUtils;
import com.sun.btrace.annotations.BTrace;
import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.OnMethod;
import com.sun.btrace.annotations.Location;

/**
 *
 * 
 * @author linwn@ucweb.com
 * @createDate 2014-5-8
 * 
 */
@BTrace(unsafe=false)
public class MngRequestTraceScript {
    @OnMethod(
            clazz = "ka.management.service.MngRequestService",
            method = "dealUnitRequest",
            location = @Location(value = Kind.ENTRY))
    public static void getParams(AnyType[] args){
        println("=======dealUnitRequest get Params===========");
        println(BTraceUtils.timestamp());
        //println(BTraceUtils.);
        printArray(args);
    }
    
   
  
    @OnMethod(
            clazz = "ka.management.service.MngRequestService",
            method = "dealOneRequestByUnit",
            location = @Location(value = Kind.THROW))
    public static void getThrow(Throwable e){
        println("=====dealOneRequestByUnit getThrow========");
        println(BTraceUtils.timestamp());
        jstack(e);
    }
    
    @OnMethod(
            clazz = "ka.management.service.MngRequestService",
            method = "dealOneRequestByUnit",
            location = @Location(value = Kind.CATCH))
    public static void doCatch(Exception e){
        println("=====dealOneRequestByUnit doCatch========");
        println(BTraceUtils.timestamp());
        jstack(e);
    }
}
