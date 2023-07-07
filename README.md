# voltdb-autojar

This utility allows you to bypass the process of creating a separate JAE file for your stored procedures
as documented in the [manual](https://docs.voltdb.com/UsingVoltDB/designappprocinstall.php).

Usage:

Assuming you have three stored procedures in 'com.test' you add the IsAVoltDBProcedure at the class level 
to each of them:

    package com.test;
    
    import org.voltdb.autojar.IsAVoltDBProcedure;

    @IsAVoltDBProcedure
    public class TestClass {
    ...
    }
    

 Once you have a VoltDB client you can then loaded the classes into VoltDB from you IDE as part of a test program:
 
       Client c = connectVoltDB("localhost");
       AutoJar.load("com.test",c,null);
       
       
 Output should look like this:
 
    2019-06-29 17:17:48:Logging into VoltDB
    2019-06-29 17:17:48:Connect to localhost...    
    2019-06-29 17:17:48:Creating JAR file for com.test in /var/folders/_q/72x6g98n181_hgnl52h3bbfm0000gn/T/AutoJar3443781839225915372.jar
    2019-06-29 17:17:48:Adding class com.test.TestClass
    2019-06-29 17:17:48:Adding class com.test.TestClass3
    2019-06-29 17:17:48:Adding class com.test.TestClass2
    2019-06-29 17:17:48:Calling @UpdateClasses to load JAR file containing procedures
    2019-06-29 17:17:48:com.test classes loaded...
    2019-06-29 17:17:48:Deleted /var/folders/_q/72x6g98n181_hgnl52h3bbfm0000gn/T/AutoJar3443781839225915372.jar
    
    
   Examples of it in use are here:
   
   https://github.com/srmadscience/voltdb-javacache/blob/main/serverSrc/jsr107/Invoke.java
   
   And here:
   
   https://github.com/srmadscience/voltdb-javacache/blob/main/src/org/voltdb/jsr107/VoltDBCache.java#L107