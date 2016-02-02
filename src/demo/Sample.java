/**
 * 
 */
package demo;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2014-2-10
 * 
 */
public class Sample {

    private static int index = 0 ;
    private String name;
    
    public static void main(String[] args) throws Exception{
        Sample sample = new Sample();
        while(true){
            try{
                sample.execute("linwn", index);
            }catch(Exception e){
                
            }
            index++;
            Thread.sleep(2000);
        }
    }
    
    public String execute(String name,int index){
        if(index%5 == 0){
            throw new RuntimeException("index="+index);
        }
        this.name = name + index;
        return this.name;
    }
}
