/**
 * 
 */
package demo;



import java.lang.reflect.Field;

import com.sun.btrace.BTraceUtils;
import com.sun.btrace.BTraceUtils.Strings;
import com.sun.btrace.annotations.BTrace;
import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.OnMethod;
import com.sun.btrace.annotations.Location;
import com.sun.btrace.annotations.Return;
import com.sun.btrace.annotations.Self;
/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2014-2-10
 * 
 */
@BTrace
public class Script {

    @OnMethod(
            clazz = "demo.Sample",
            method = "execute",
            type = "java.lang.String(java.lang.String,int)",
            location = @Location(value = Kind.RETURN))
    public static void getReturn(@Return String ret){
        BTraceUtils.println("=======get Return===========");
        BTraceUtils.println(Strings.concat("return=", ret));
    }
    
    @OnMethod(
            clazz = "demo.Sample",
            method = "execute",
            type = "java.lang.String(java.lang.String,int)",
            location = @Location(value = Kind.ENTRY))
    public static void getParams(String name,int index){
        BTraceUtils.println("=======get Params===========");
        BTraceUtils.println(Strings.concat("name=", name));
        BTraceUtils.println(Strings.concat("index=", Strings.str(index)));
    }
    
    @OnMethod(
            clazz = "demo.Sample",
            method = "execute",
            type = "java.lang.String(java.lang.String,int)",
            location = @Location(value = Kind.ENTRY))
    public static void getField(@Self Object thisObject){
        BTraceUtils.println("=======get Field===========");
        Field nameField = BTraceUtils.field("demo.Sample","name");
        Object name = BTraceUtils.get(nameField,thisObject);
        Field indexField = BTraceUtils.field("demo.Sample","index");
        Object index = BTraceUtils.get(indexField,thisObject);
        BTraceUtils.println(Strings.concat("name=", Strings.str(name)));
        BTraceUtils.println(Strings.concat("index=", Strings.str(index)));
    }
    
    @OnMethod(
            clazz = "demo.Sample",
            method = "execute",
            type = "java.lang.String(java.lang.String,int)",
            location = @Location(value = Kind.THROW))
    public static void getThrow(Throwable e){
        BTraceUtils.println("=======get Throw===========");
        BTraceUtils.jstack(e);
    }
}
