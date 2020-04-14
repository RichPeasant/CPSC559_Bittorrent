package Network.Client;

import Controller.Node;
import Network.MD5hash;
import Network.NetworkStatics;
import Network.NodeList;
import Network.QueryNodes;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

public class UDPClient extends Thread {
    private final MD5hash hasher = new MD5hash();
    private final String filename;
    private final NodeList findNodes = new NodeList();
    private final Node n;

    public UDPClient(final String filename, Node n) {
        this.filename = filename;
        this.n = n;
    }

//    public void run() {
//
//        InetAddress inetAddress = null;
//        try {
//            inetAddress = InetAddress.getByName("127.0.0.1");
//        } catch (UnknownHostException e) {
//            e.printStackTrace();
//        }
//        String url = inetAddress.getHostName();
//        ArrayList<byte[]> tempnodelist = new ArrayList<>();
//        byte[] blah = inetAddress.getAddress();
//        System.out.println(blah.length);
//        tempnodelist.add(blah);
//
//        byte[] hash = null;
//        try {
//            hash = hasher.getHashFile(Node.TESTFILE);
//        } catch (NoSuchAlgorithmException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        String mike = "127.0.0.1";
//
//        Master master = new Master(tempnodelist, filename, 201164, hash, this.n, mike.getBytes());
//        master.start();
//    }

    public void run() {
        String[] nodeList = findNodes.getNodes();
        System.out.println(Arrays.toString(nodeList));
        ArrayList<String> nlist = new ArrayList<>();

        nlist.addAll(Arrays.asList(nodeList));

        byte[] cmd = ByteBuffer.allocate(4).putInt(5).array();
        byte[] fname = ByteBuffer.allocate(32).put(filename.getBytes()).array();
        byte[] myIP = new byte[0];
        try {
            myIP = InetAddress.getByName(n.getIP()).getAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        byte[] message = new byte[40];
        System.arraycopy(cmd, 0, message, 0, cmd.length);
        System.arraycopy(fname, 0, message, cmd.length, fname.length);
        System.arraycopy(myIP, 0, message, 36, myIP.length);
        QueryNodes qNodes = new QueryNodes(message, nlist);
        byte[] queryData = null;
        try {
            queryData = qNodes.fileQuery();
        } catch (IOException e) {
            e.printStackTrace();
        }
        int queryCmd = NetworkStatics.byteArrayToInt(queryData);
        System.out.println("QUERY CMD " + queryCmd);
        ArrayList<byte[]> peerList = new ArrayList<>();
        if (queryCmd == 46) //file not found
            System.out.println("File Not Found");
        else if (queryCmd == 45) //direct peer list is head cmd4byte:filesize4byte:hash16pyte:youripbytes:ips
        {
            NetworkStatics.printPacket(queryData, "QUERY DATA CMD 45");
            int filesize = ByteBuffer.wrap(Arrays.copyOfRange(queryData, 4, 8)).getInt();
            byte[] hash = Arrays.copyOfRange(queryData, 8, 24);
            byte[] hip = Arrays.copyOfRange(queryData, 24, 28);
            for (int i = 28; i < queryData.length; i += 4) {
                byte[] b = Arrays.copyOfRange(queryData, i, i + 4);
                peerList.add(b);
            }
            Master master = new Master(peerList, this.filename, filesize, hash, this.n, hip);
            master.start();
        }
        else
        {
            byte[] headip = Arrays.copyOfRange(queryData, 8, 12);
            String hd = null;
            try {
                hd = InetAddress.getByAddress(headip).getHostAddress();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            System.out.println(hd);
            byte[] trackerip = Arrays.copyOfRange(queryData, 12, 16);
            String td = null;
            try {
                td = InetAddress.getByAddress(trackerip).getHostAddress();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            System.out.println(td);
            ArrayList<byte[]> peerData = new ArrayList<byte[]>();
            try {
                peerData = getPeerData(hd);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (peerData.isEmpty()) {
                try {
                    headip = startElection(td);
                    hd = InetAddress.getByAddress(headip).getHostAddress(); //check
                    peerData = getPeerData(hd);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            byte[] fsize = peerData.get(0);
            int fiSize = ByteBuffer.wrap(fsize).getInt();
            byte[] fhash = peerData.get(1);
            byte[] plist = peerData.get(2);

            for (int i = 0; i < plist.length; i += 4) {
                byte[] b = Arrays.copyOfRange(plist, i, i + 4);
                peerList.add(b);
                NetworkStatics.printPacket(b, "PEER ADDED");
            }
            Master master = new Master(peerList, this.filename, fiSize, fhash, this.n, headip);
            master.start();
        }
    }

    public byte[] startElection(String addr) throws IOException {
        InetAddress ip = InetAddress.getByName(addr);
        DatagramSocket udpSocket = new DatagramSocket(6091);
        byte[] cmd = ByteBuffer.allocate(4).putInt(24).array();
        byte[] fname = filename.getBytes();
        byte[] len = ByteBuffer.allocate(4).putInt(fname.length).array();
        byte[] out = new byte[8 + fname.length];
        System.arraycopy(cmd, 0, out, 0, cmd.length);
        System.arraycopy(len, 0, out, cmd.length, len.length);
        System.arraycopy(fname, 0, out, 8, fname.length);
        byte[] bytes = new byte[NetworkStatics.MAX_PACKET_SIZE];
        DatagramPacket packet = new DatagramPacket(out, out.length, ip, NetworkStatics.SERVER_CONTROL_RECEIVE);
        udpSocket.send(packet);
        DatagramPacket recvpacket = new DatagramPacket(bytes, bytes.length);
        System.out.println("sent about to receive");
        udpSocket.receive(recvpacket);
        System.out.println("election receive");
        byte[] nout = new byte[packet.getLength()];
        System.arraycopy(bytes, 0, nout, 0, nout.length);
        udpSocket.close();
        return nout;
    }

    public ArrayList<byte[]> getPeerData(String addr) throws IOException {
        InetAddress ip = InetAddress.getByName(addr);
        DatagramSocket udpSocket = new DatagramSocket(6090);
        ArrayList<byte[]> peerData = new ArrayList<byte[]>();
        byte[] bytes = new byte[NetworkStatics.MAX_PACKET_SIZE];
        byte[] cmd = ByteBuffer.allocate(4).putInt(5).array();
        byte[] fname = ByteBuffer.allocate(32).put(filename.getBytes()).array();
        byte[] message = new byte[36];
        System.arraycopy(cmd, 0, message, 0, cmd.length);
        System.arraycopy(fname, 0, message, cmd.length,fname.length);
        DatagramPacket packet = new DatagramPacket(message, message.length, ip, NetworkStatics.SERVER_CONTROL_RECEIVE);
        udpSocket.send(packet);
        packet = new DatagramPacket(bytes, bytes.length);

        try {
            udpSocket.setSoTimeout(1500);
            udpSocket.receive(packet);
        } catch (SocketTimeoutException e) {
            udpSocket.close();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[] nout = new byte[packet.getLength()];
        System.arraycopy(bytes, 0, nout, 0, nout.length);

        byte[] filesize = Arrays.copyOfRange(nout, 4, 8);
        peerData.add(filesize);
        byte[] filehash = Arrays.copyOfRange(nout, 8, 24);
        peerData.add(filehash);
        byte[] peerList = Arrays.copyOfRange(nout, 28, nout.length);
        peerData.add(peerList);

        udpSocket.close();
        return peerData;
    }
}
