import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Client {
    public static final String HOSTNAME = "attu2.cs.washington.edu";
    // TODO: change to 12235
    public static final int PORTNUMBER = 38800;
    public static final int HEADERSPACE = 12;
    public static final short STEP1 = 1;
    public static final int PAYLOAD = 16;
    public static void main(String[] args)  {
        try {
            // Stage A: send the udp packets with string "hello world"
            System.out.println("Connecting to the server...");
            DatagramSocket socket = new DatagramSocket();
            InetAddress address = InetAddress.getByName(HOSTNAME);
            System.out.println("Stage A running...");
            // String sendString = "hello world\0";
            String sendString = "hello\0";
            
            // question: for the helloworld packet, what's the psecret, and do we need to set the header?
            byte[] sendBuffer = bufferCreate(sendString.getBytes("UTF-8"), 0, STEP1);
            // buffer: the packet data "hello world"
            // length: the packet length
            // address: the ip address of attu2.cs.washington.edu
            // port: the port number listen to
            DatagramPacket packet = new DatagramPacket(sendBuffer, sendBuffer.length, address, PORTNUMBER);
            socket.send(packet);

            // receive the response packets from the server
            // the receiver buffer should receive a payload with header, the total would by
            // 12 + 16 = 28 bytes
            byte[] receiveBuffer = new byte[HEADERSPACE + PAYLOAD];
            packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(packet);

            // question 2: Do client need to check the header?
            int[] receiveData = receiveHandler(receiveBuffer);
            int num = receiveData[0];
            int len = receiveData[1];
            int udpPort = receiveData[2];
            int secretA = receiveData[3];
            System.out.println("num is : " + num);
            System.out.println("len is : " + len);
            System.out.println("UDP port number is : " + udpPort);
            System.out.println("Secret message A : " + secretA);
            // the packets already sent to server and get ACK back
            int send = 0;

            // Stage B
            // receive the ack number from the server
            // wait 0.5s
            System.out.println("Stage B running");
            socket.setSoTimeout(500);
            while (send < num) {
                // change the send buffer to be the structure with first 4 bytes representing the
                // id and the remaining are all zeros.
                sendBuffer = bufferCreate(packetHandler(send, len), secretA, STEP1);
                packet = new DatagramPacket(sendBuffer, sendBuffer.length, address, udpPort);
                socket.send(packet);
                try {
                    // the ack received, size should be 4 + 12 = 16 bytes
                    receiveBuffer = new byte[HEADERSPACE + 4];
                    socket.receive(new DatagramPacket(receiveBuffer, receiveBuffer.length));
                    send++;
                    System.out.println(send);
                } catch (SocketTimeoutException e) {
                    System.out.println("timeout! ACK not receive, resending...");
                    continue;
                }
            }

            // Stage b2, server sends udp packet to client with TCP port number and secreB
            // the buffer size would be 12 + 8 (2 int) = 20
            receiveBuffer = new byte[HEADERSPACE + 8];
            socket.receive(new DatagramPacket(receiveBuffer, receiveBuffer.length));
            ByteBuffer byteBuffer = ByteBuffer.wrap(receiveBuffer);
            headerHandler(byteBuffer);
            int tcpPort = byteBuffer.getInt();
            int secretB = byteBuffer.getInt();
            System.out.println("TCP port number is : " + tcpPort);
            System.out.println("Secret message B : " + secretB);
            socket.close();

            // Stage C: it handles the tcp socket in stage c and build a connection socket using tcp,
            // the parameters are host address and the tcp port number. It basically extract the
            // information in the packet.
            System.out.println("Stage C running...");
            Socket socketTCP = new Socket(address, tcpPort);
            InputStream in = socketTCP.getInputStream();
            OutputStream out = socketTCP.getOutputStream();
            receiveBuffer = new byte[HEADERSPACE + 16];
            in.read(receiveBuffer);
            byteBuffer = ByteBuffer.wrap(receiveBuffer);
            // extract the header from the packet
            headerHandler(byteBuffer);
            int num2 = byteBuffer.getInt();
            int len2 = byteBuffer.getInt();
            int secretC = byteBuffer.getInt();
            char c = byteBuffer.getChar();
            System.out.println("num2 : " + num2);
            System.out.println("len2 : " + len2);
            System.out.println("Secret message c : " + secretC);
            System.out.println("c: " + c);

            // Stage D: tcp send num2 payloads and the length of payload is len2,
            // all of the content of the payload are c.
            System.out.println("Stage D running...");
            int payload_d1_len = (len2 % 4 == 0) ? len2 : (len2 / 4 * 4 + 4);  // 4-byte alignment
            sendBuffer = new byte[payload_d1_len];
            // sendBuffer = new byte[len2];
            ByteBuffer sendBuffer_d1 = ByteBuffer.wrap(sendBuffer);
            for (int i = 0; i < payload_d1_len / 2; i++) {
                sendBuffer_d1.putChar(c);
            }
            sendBuffer = sendBuffer_d1.array();
            for (int i = 0; i < num2; i++) {
                out.write(bufferCreate(sendBuffer, secretC, STEP1));
            }

            // receive the secretD message from the server
            receiveBuffer = new byte[HEADERSPACE + 4];
            in.read(receiveBuffer);
            byteBuffer = ByteBuffer.wrap(receiveBuffer);
            headerHandler(byteBuffer);
            int secretD = byteBuffer.getInt();
            System.out.println("Secret message d : " + secretD);
            // close the socket
            socketTCP.close();
            in.close();
            out.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // this method is used for stage B, step b1. It will allocate space for the byte[] with packet_id
    // the first four bytes, and the rest of bytes are 0s.
    private static byte[] packetHandler(int i, int len) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(len + 4);
        byteBuffer.putInt(i);
        return byteBuffer.array();
    }

    // this method will return the server responded UDP packet with an integer array, which contains
    // num, len, udp_port, and secretA.
    // this method will not check if the header is valid or not.
    private static int[] receiveHandler(byte[] receiveBuffer) {
        int[] res = new int[4];
        ByteBuffer byteBuffer = ByteBuffer.wrap(receiveBuffer);
        headerHandler(byteBuffer);
        for (int i = 0; i < 4; i++) {
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

    // this method will allocate space for the header and the packet. The header consists of
    // payload length (4 bytes), the payload size doesn't include the length of header,
    // psecret (4 bytes), step (2 bytes) and student number (2 bytes), a total
    // of 12 bytes.
    private static byte[] bufferCreate(byte[] buffer, int pSecret, short step) {
        // Student number : 1836832
        short studentNumber = 832;
        // the alignment of the payload is 4, add padding bytes.
        int bufferSpace = buffer.length;
        while (bufferSpace % 4 != 0) {
            bufferSpace++;
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSpace + HEADERSPACE);
        byteBuffer.putInt(bufferSpace);
        byteBuffer.putInt(pSecret);
        byteBuffer.putShort(step);
        byteBuffer.putShort(studentNumber);
        byteBuffer.put(buffer);
        return byteBuffer.array();
    }
}
