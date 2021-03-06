import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;
import java.nio.BufferUnderflowException;
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
    protected DatagramPacket packet = null;
    protected Random rand = null;
    protected byte[] receive_buffer = new byte[1024];
    public static final int PORT_NUM = 12235;
    public static final int HEADERSPACE = 12;
    public static final short STEP1 = 1;
    public static final short STEP2 = 2;
    // Student number : 1836832
    public static final short STUDENT_NUM = 832;
    public static final int LARGE_BUFFER_SIZE = 2048;
    private static int tcp_port;

    public ServerThread(String name, DatagramPacket packet, byte[] byteBuffer, DatagramSocket socket) throws IOException {
        this.thread_name = name;
        this.packet = packet;
        this.receive_buffer = byteBuffer;
        this.socket = socket;
        this.rand = new Random();
    }

    public void run() {
        try {
            // Stage A
            // receiving packet
            System.out.println("Stage A running...");
            //byte[] receive_buffer = new byte[HEADERSPACE + "hello world\0".length()];

            // return false if header is invalid
            if (!verifyHeader(receive_buffer, "hello world\0".length(), 0, STEP1, STUDENT_NUM)) {
                System.out.println("    Stage a1 Header fail");
                return;
            }
            // verify packet length
            if (packet.getLength() != (HEADERSPACE + 12)) {
                System.out.println("    Stage a1 packet length fail");
                return;
            }
            // verify received message "hello world\0"
            String result = new String(packet.getData(), HEADERSPACE, "hello world\0".length());
            if (!result.equals("hello world\0")) {
                System.out.println("    Wrong message received in Stage A");
                return;
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
            System.out.println("Stage B running...");
            int count = 0;
            int payload_b1_len = (len % 4 == 0) ? len : (len / 4 * 4 + 4);  // 4-byte alignment

            //  b1
            Boolean ack = false;
            this.socket = new DatagramSocket(udp_port);
            // 3 second
            while (count < num) {
                //receive_buffer = new byte[HEADERSPACE + 4 + payload_b1_len];  // 4: packet_id length
                receive_buffer = new byte[1024];
                packet = new DatagramPacket(receive_buffer, receive_buffer.length);
                try {
                    socket.setSoTimeout(3000);
                    socket.receive(packet);
                } catch (SocketTimeoutException e) {
                    System.out.println("    Stage b1 socket time out");
                    socket.close();
                    return;
                }
                // return false if header is invalid
                if (!verifyHeader(receive_buffer, payload_b1_len + 4, secretA, STEP1, STUDENT_NUM)) {
                    socket.close();
                    System.out.println("    Stage b1 Header fail");
                    return;
                }
                // verify packet length
                if (packet.getLength() != (HEADERSPACE + payload_b1_len + 4)) {
                    socket.close();
                    System.out.println("    Stage b1 packet length fail");
                    return;
                }

                int[] payload_b1 = receiveHandler(receive_buffer, payload_b1_len / 4 + 1);
                // verify payload
                boolean all_zero = true;
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
                    System.out.println("    Stage b1 payload fail");
                    return;
                }
                // ack
                if (!ack) { // at lease one !ack
                    System.out.println("    Don't ack packet_id: " +  payload_b1[0]);
                    ack = true;
                    continue;
                }
                if (rand.nextBoolean()) {
                    System.out.println("    Ack packet_id: " +  payload_b1[0]);
                    ByteBuffer acked_packet_id = ByteBuffer.allocate(4);
                    acked_packet_id.putInt(payload_b1[0]);
                    byte[] ack_payload = b.array();
                    send_buffer = bufferCreate(ack_payload, secretA, STEP2);
                    client_addr = packet.getAddress();
                    client_port = packet.getPort();
                    packet = new DatagramPacket(send_buffer, send_buffer.length, client_addr, client_port);
                    socket.send(packet);
                    count++;
                } else {
                    System.out.println("    Don't ack packet_id: " +  payload_b1[0]);
                }
            }

            // b2
            ByteBuffer byteBuffer = ByteBuffer.allocate(8);
            tcp_port = (int) (Math.random() * (65535 - 49152 + 1) + 49152); // Ephemeral port range [49152, 65535)
            int secretB = (int) (Math.random() * (500 - 5 + 1) + 5);            // [5, 500);
            System.out.println("    tcp port = " + tcp_port);
            System.out.println("    secret B = " + secretB);
            byteBuffer.putInt(tcp_port);
            byteBuffer.putInt(secretB);
            byte[] payload_b2 = byteBuffer.array();
            send_buffer = bufferCreate(payload_b2, secretB, STEP2);
            client_addr = packet.getAddress();
            client_port = packet.getPort();
            packet = new DatagramPacket(send_buffer, send_buffer.length, client_addr, client_port);
            socket.send(packet);
            socket.close();
            System.out.println("Stage B finished...\n\n");


            System.out.println("Stage C running...");
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

            // Step c1
            this.serverSocket = new ServerSocket(tcp_port);
            System.out.println("create server socket succeeded.");
            Socket tcp_socket = serverSocket.accept();
            System.out.println("Accept tcp socket succeeded.");
            int[] param = stepc1(tcp_socket);
            System.out.println("Stage C finished...\n\n");

            // Step d1 & d2
            System.out.println("Stage D started...\n\n");
            stepd(tcp_socket, param);
            System.out.println("Stage D finished...\n\n");
            serverSocket.close();
        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static int[] stepc1(Socket socket) throws IOException {
        ByteBuffer c1buffer = ByteBuffer.allocate(14);
        int num2 = (int) (Math.random() * (10 - 5 + 1) + 5);     // [5, 10)
        int len2 = (int) (Math.random() * (500 - 5 + 1) + 5);    // [5, 500)
        int secretC = (int) (Math.random() * (500 - 5 + 1) + 5);// [5, 500)
        char c = (char) ((int)(Math.random() * 256));
        System.out.println("    num2: " + num2);
        System.out.println("    len2: " + len2);
        System.out.println("    secretC: " + secretC);
        System.out.println("    char c: " + c);
        c1buffer.putInt(num2);          // num2
        c1buffer.putInt(len2);          // len2
        c1buffer.putInt(secretC);      // secretC
        c1buffer.putChar(c);
        byte[] c1payload = c1buffer.array();
        byte[] c1send_buffer = bufferCreate(c1payload, secretC, STEP2);
        // send to client
        OutputStream output = socket.getOutputStream();
        output.write(c1send_buffer);
        int[] param = {num2, len2, secretC, (int) c};
        return param;
    }

    private void stepd(Socket socket, int[] param) throws  IOException {
        // step d1
        InputStream input = socket.getInputStream();
        socket.setSoTimeout(3000);
        int num2 = param[0];
        int len2 = param[1];
        int secretC = param[2];
        char c = (char) param[3];
        System.out.println("    num2: " + num2);
        System.out.println("    len2: " + len2);
        System.out.println("    secretC: " + secretC);
        System.out.println("    char c: " + c);
        int payload_d1_len = (len2 % 4 == 0) ? len2 : (len2 / 4 * 4 + 4);  // 4-byte alignment

        while (num2 > 0) {
             byte[] line = new byte[payload_d1_len + HEADERSPACE];    // A large enough buffer to avoid bufferoverflow
             input.read(line);

            // Verify header size.
            if (!verifyHeader(line, payload_d1_len, secretC, STEP1, STUDENT_NUM)) {
                socket.close();
                this.serverSocket.close();
                System.out.println("    Stage d1 header fail");
                return;
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(line);
            headerHandler(byteBuffer);
            try {
                for (int i = 0; i < payload_d1_len/ 2; i++) {
                    // char cur = (char) byteBuffer.get();
                    char cur = byteBuffer.getChar();
                    if (cur != c) {
                        System.out.println("    Expected \'" + (int) c + "\' but received \'" + (int) cur + "\' at index " + i);
                        this.serverSocket.close();
                        System.out.println("    Stage d1 payload content fail");
                        return;
                    }
                }
            } catch (BufferUnderflowException e){
                this.serverSocket.close();
                System.out.println("    Stage d1 payload length is too short");
            }
            num2--;
        }

        // step d2
        ByteBuffer d2buffer = ByteBuffer.allocate(4);
        int secretD = (int) (Math.random() * (500 - 5 + 1) + 5);// [5, 500)
        System.out.println("    secretD: " + secretD);
        OutputStream output = socket.getOutputStream();
        d2buffer.putInt(secretD);

        byte[] d2payload = d2buffer.array();
        byte[] d1send_buffer = bufferCreate(d2payload, secretD, STEP2);
        output.write(d1send_buffer);
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
        int a = 0, b = 0, c= 0, d = 0;
        if ( (a = byteBuffer.getInt()) != payload_len || (b = byteBuffer.getInt()) != psecret ||
                (c =  byteBuffer.getShort()) != step || (d = byteBuffer.getShort()) != studentNumber) {

            System.out.println("len " + a);
            System.out.println("psecret " + b);
            System.out.println("step " + c);
            System.out.println("studentNumber " + d);
            System.out.println("len " + payload_len);
            System.out.println("psecret " + psecret);
            System.out.println("step " + step);
            System.out.println("studentNumber " + studentNumber);

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
        byteBuffer.putInt(bufferSpace);
        byteBuffer.putInt(pSecret);
        byteBuffer.putShort(step);
        byteBuffer.putShort(STUDENT_NUM);
        byteBuffer.put(buffer);
        return byteBuffer.array();
    }
}