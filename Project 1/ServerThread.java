import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

/**
 * This thread is responsible to handle client connection for multi-thread usage.
 *
 * @author www.codejava.net
 */

public class ServerThread extends Thread{
    //??
    protected ServerSocket serverSocket = null;
    protected BufferedReader in = null;

    protected String thread_name = null;
    protected DatagramSocket socket = null;
    protected Random rand = null;

    public static final int PORT_NUM = 12235;
    public static final int HEADERSPACE = 12;
    public static final short STEP1 = 1;
    public static final short STEP2 = 2;
    // Student number : 1836832
    public static final short STUDENT_NUM = 832;

    public ServerThread() throws IOException {
        this("ServerThread");
    }

    public ServerThread(String name) throws IOException {
        this.thread_name = name;
        this.socket = new DatagramSocket(PORT_NUM);
        this.rand = new Random();

        //??
        this.serverSocket = new ServerSocket(PORT_NUM);
    }

    public void run() {
        try (
            Socket clientSocket = serverSocket.accept();

            InputStream input = clientSocket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));

            OutputStream output = clientSocket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

        ) {
            // 3 second
            socket.setSoTimeout(3000);
            // Stage A
            // receiving packet
            System.out.print("Stage A running...");
            //byte[] receive_buffer = new byte[HEADERSPACE + "hello world\0".length()];
            byte[] receive_buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(receive_buffer, receive_buffer.length);
            try {
                socket.receive(packet);
            } catch (SocketTimeoutException e) {
                System.out.println("    Stage a1 socket time out");
                socket.close();
                return;
            }
            // return false if header is invalid
            if (!verifyHeader(receive_buffer, "hello world\0".length(), 0, STEP1, STUDENT_NUM)) {
                socket.close();
                System.out.println("    Stage a1 Header fail");
                return;
            }
            // verify packet length
            if (packet.getLength() != (HEADERSPACE + 12)) {
                socket.close();
                System.out.println("    Stage a1 packet length fail");
                return;
            }
            // verify received message "hello world\0"
            String result = new String(packet.getData(), HEADERSPACE, "hello world\0".length());
            if (!result.equals("hello world\0")) {
                System.out.println("    Wrong message received in Stage A");
            } else {
                System.out.println("    Message Received: " + result);
            }
            // prepare sending packet
            ByteBuffer b = ByteBuffer.allocate(16);
            int num = (int) (Math.random() * (10 - 5 + 1) + 5);     // [5, 10)
            int len = (int) (Math.random() * (500 - 5 + 1) + 5);    // [5, 500)
            int udp_port = (int) (Math.random() * (65535 - 49152 + 1) + 49152);   // Ephemeral port range [49152, 65535)
            int secretA = (int) (Math.random() * (500 - 5 + 1) + 5);// [5, 500)
            System.out.println("    num: " + num);
            System.out.println("    len: " + len);
            System.out.println("    udp_port: " + udp_port);
            System.out.println("    secretA: " + secretA);
            b.putInt(num);          // num
            b.putInt(len);          // len
            b.putInt(udp_port);     // udp_port
            b.putInt(secretA);      // secretA
            byte[] payload_a2 = b.array();
            byte[] send_buffer = bufferCreate(payload_a2, secretA, (short) 2);
            // send packet
            InetAddress client_addr = packet.getAddress();
            int client_port = packet.getPort();
            packet = new DatagramPacket(send_buffer, send_buffer.length, client_addr, client_port);
            socket.send(packet);
            System.out.println("Stage A finished...\n\n");


            // Stage B
            System.out.print("Stage B running...");
            int count = 0;
            int payload_b1_len = (len % 4 == 0) ? len : (len / 4 * 4 + 4);  // 4-byte alignment

            //  b1
            Boolean ack = false;
            while (count < num) {
                //receive_buffer = new byte[HEADERSPACE + 4 + payload_b1_len];  // 4: packet_id length
                receive_buffer = new byte[1024];
                this.socket = new DatagramSocket(udp_port);
                packet = new DatagramPacket(receive_buffer, receive_buffer.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException e) {
                    System.out.println("    Stage b1 socket time out");
                    socket.close();
                    return;
                }
                // return false if header is invalid
                if (!verifyHeader(receive_buffer, payload_b1_len, secretA, STEP1, STUDENT_NUM)) {
                    socket.close();
                    System.out.println("    Stage b1 Header fail");
                    return;
                }
                // verify packet length
                if (packet.getLength() != (HEADERSPACE + payload_b1_len)) {
                    socket.close();
                    System.out.println("    Stage b1 packet length fail");
                    return;
                }

                int[] payload_b1 = receiveHandler(receive_buffer, payload_b1_len / 4 + 1);
                // verify payload
                boolean all_zero = true;
                System.out.println("    packet_id: " +  payload_b1[0]);
                if (payload_b1[0] != count) {
                    socket.close();
                    System.out.println("    Stage b1 packet_id fail");
                    return;
                }
                for (int i = 1; i < payload_b1.length; i++) {
                    if (payload_b1[i] != 0) {
                        System.out.println("    receive non-zero at index: " + count);
                        all_zero = false;
                    }
                }
                if (!all_zero) {
                    socket.close();
                    System.out.println("    Stage b1 Header fail");
                    return;
                }
                // ack
                if (!ack) { // at lease one !ack
                    ack = true;
                    continue;
                }
                if (rand.nextBoolean()) {
                    ByteBuffer acked_packet_id = ByteBuffer.allocate(4);
                    acked_packet_id.putInt(payload_b1[0]);
                    byte[] ack_payload = b.array();
                    send_buffer = bufferCreate(ack_payload, secretA, STEP2);
                    client_addr = packet.getAddress();
                    client_port = packet.getPort();
                    packet = new DatagramPacket(send_buffer, send_buffer.length, client_addr, client_port);
                    socket.send(packet);
                    count++;
                }
            }
            // b2
            ByteBuffer byteBuffer = ByteBuffer.allocate(8);
            int tcp_port = (int) (Math.random() * (65535 - 49152 + 1) + 49152); // Ephemeral port range [49152, 65535)
            int secretB = (int) (Math.random() * (500 - 5 + 1) + 5);            // [5, 500);
            byteBuffer.putInt(tcp_port);
            byteBuffer.putInt(secretB);
            byte[] payload_b2 = b.array();
            send_buffer = bufferCreate(payload_b2, secretA, STEP2);
            client_addr = packet.getAddress();
            client_port = packet.getPort();
            packet = new DatagramPacket(send_buffer, send_buffer.length, client_addr, client_port);
            socket.send(packet);
            System.out.println("Stage B finished...\n\n");


            System.out.print("Stage C running...");

            System.out.println("Stage C finished...\n\n");

            /*
            String stageA = null;
            if (reader == null) {
                System.out.println("fail to build input reader");
            } else {
                if ((stageA = reader.readLine()) == null) {
                    reader.close();
                    System.out.println("receive no message in stage A");
                }
            }
            read_buf = stageA.getBytes();
            System.out.println("Stage A message: " + Arrays.toString(read_buf));
*/
            /*
            String text;
            do {
                text = reader.readLine();
                String reverseText = new StringBuilder(text).reverse().toString();
                writer.println("Server: " + reverseText);

            } while (!text.equals("bye"));
            */
            serverSocket.close();
        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }


    private static int[] receiveHandler(byte[] receiveBuffer, int len) {
        int[] res = new int[len];
        ByteBuffer byteBuffer = ByteBuffer.wrap(receiveBuffer);
        headerHandler(byteBuffer);
        for (int i = 0; i < len; i++) {
            res[i] = byteBuffer.getInt();
        }

        return res;
    }

    private static void headerHandler(ByteBuffer byteBuffer) {
        byteBuffer.getInt(); // payload_len
        byteBuffer.getInt(); // psecret
        byteBuffer.getShort(); // step
        byteBuffer.getShort(); // last 3 digits of student number
    }

    // return true if header is good; otherwise, return false.
    private static boolean verifyHeader(byte[] receiveBuffer, int payload_len,
                                        int psecret, short step, short studentNumber) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(receiveBuffer);
        if (byteBuffer.getInt() != payload_len || byteBuffer.getInt() != psecret ||
            byteBuffer.getShort() != step || byteBuffer.getShort() != studentNumber) {
            return false;
        }
        return true;
    }

    private static byte[] bufferCreate(byte[] buffer, int pSecret, short step) {

        // the alignment of the payload is 4, add padding bytes.
        int bufferSpace = buffer.length;
        while (bufferSpace % 4 != 0) {
            bufferSpace++;
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSpace + HEADERSPACE);
        byteBuffer.putInt(buffer.length);
        byteBuffer.putInt(pSecret);
        byteBuffer.putShort(step);
        byteBuffer.putShort(STUDENT_NUM);
        byteBuffer.put(buffer);
        return byteBuffer.array();
    }
}
