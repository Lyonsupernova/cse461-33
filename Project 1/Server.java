import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Server {
    // TODO: change to 12235
    public static final int PORTNUMBER = 38800;
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
                System.out.println("socket connected");
                new ServerThread("Thread " + name++, packet, byteBuffer, udp_socket).start();
            }
        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
