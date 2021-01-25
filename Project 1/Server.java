import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Server {
    public static final int PORTNUMBER = 12235;
    public static final int HEADERSPACE = 12;
    public static final short STEP1 = 1;
    public static final int PAYLOAD = 16;
    public static int name = 1;

    public static void main(String[] args) {

        try {
           DatagramSocket udp_socket = new DatagramSocket(PORTNUMBER);
           udp_socket.setSoTimeout(0);

            while (true) {
                byte[] byteBuffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(byteBuffer, byteBuffer.length);
                udp_socket.receive(packet);
                new ServerThread("Thread " + name++, packet, byteBuffer).start();
            }

        } catch (IOException e) {

        }


    }
}
