import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Server {
    // TODO: change to 12235
    public static final int PORTNUMBER = 38800;
    public static int name = 1;

    public static void main(String[] args) {

        try {
            DatagramSocket udp_socket = new DatagramSocket(PORTNUMBER);
            while (true) {
                udp_socket.setSoTimeout(0);
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
