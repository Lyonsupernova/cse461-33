import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Server {
    public static void main(String[] args) throws Exception {
        //??
        ServerThread server1 = new ServerThread("A");
        server1.start();
        Thread.sleep(1000); // wait one sec before new thread

        ServerThread server2 = new ServerThread("B");
        server2.start();
        Thread.sleep(1000);
    }


}
