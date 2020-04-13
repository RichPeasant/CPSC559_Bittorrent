package Network.Client;
import Network.CommandHandler;
import Network.NetworkStatics;
import Network.MD5hash;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class Slave extends Thread {

	private DatagramSocket udpSocket;
	private int bytestart;
	private int bytefinish;
	private int numPackets;
	private String filename;
	private InetAddress addr;
	protected BlockingQueue<byte[]> queue = null;
	private MD5hash util = new MD5hash();
	private Map map = new ConcurrentHashMap<Integer,byte[]>();
	private int packetsize;

	public Slave(final InetAddress addr, final int port, int bytestart, int bytefinish, final String filename, BlockingQueue<byte[]> queue) throws IOException
	{
	 	this.addr = addr;
	 	this.bytestart = bytestart;
	 	this.bytefinish = bytefinish;
	 	this.filename = filename;
	 	this.udpSocket= new DatagramSocket(port);
	 	this.queue = queue;
	 	this.numPackets = ((bytefinish-bytestart)/(NetworkStatics.FILECHUNK_SIZE))+1;
	 	this.packetsize = NetworkStatics.FILECHUNK_SIZE;
		System.out.println("requesting range start index of " + this.bytestart + " and end index of " + this.bytefinish);
		System.out.println("packet count: " + this.numPackets);
	}

	public void run()
	{
		byte[] out = prepareRange(bytestart,bytefinish);
		NetworkStatics.printPacket(out, "CMD 10 Request");
		Receiver receiveThread = new Receiver(this, udpSocket);
		receiveThread.start(); //start receiver thread
		CommandHandler handl = new CommandHandler();
		DatagramPacket dp = new DatagramPacket(out, out.length, addr, NetworkStatics.SERVER_CONTROL_RECEIVE); //init packet and bind addr,port
		try {
			udpSocket.send(dp); //sent message/packet
			System.out.println("request sent...");
		} catch (IOException e) {
			e.printStackTrace();
		}

		try{
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		if (receiveThread.isAlive()) {
			receiveThread.shutdown();
			try {
				System.out.println("joining receivethread...");
				receiveThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("receivethread joined.");
		while(isMissing())
		{
			System.out.println("packets missing, re-requesting...");
			Receiver rangeThread = new Receiver(this, udpSocket);
			rangeThread.start();

			for(int i=0;i<numPackets;i++)
			{
				if(map.get(i)==null)
				{
					byte[] fnbytes = this.filename.getBytes();
					byte[] data = new byte[12+fnbytes.length];
					System.arraycopy(NetworkStatics.intToByteArray(this.bytestart), 0, data, 0, 4);
					System.arraycopy(NetworkStatics.intToByteArray(this.bytefinish), 0, data, 4, 4);
					System.arraycopy(NetworkStatics.intToByteArray(i), 0, data, 8, 4);
					System.arraycopy(fnbytes,0,data,12,fnbytes.length);
					byte[] rangeRequest = handl.generatePacket(12, data);
					NetworkStatics.printPacket(rangeRequest, "SLAVE RANGE REQUEST");
					DatagramPacket packet = new DatagramPacket(rangeRequest,rangeRequest.length,addr,NetworkStatics.SERVER_CONTROL_RECEIVE);
					try {
						udpSocket.send(packet);
					} catch (IOException ex) {
						ex.printStackTrace();
					}
				}
			}
			if (rangeThread.isAlive()) {
				rangeThread.shutdown();
				try {
					rangeThread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		try {
			queue.put(createChunk());
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		udpSocket.close();

	}

	public void processPacket(byte[] bytes) throws InterruptedException, NoSuchAlgorithmException {
		//NetworkStatics.printPacket(bytes, "PACKET TO PROCESS");
		System.out.println("processing packet of len " + bytes.length);
		byte[] seqbyte = Arrays.copyOfRange(bytes,8,12);
		int seqnum = ByteBuffer.wrap(seqbyte).getInt();
		byte[] hashSent = Arrays.copyOfRange(bytes,12,28);
		byte[] message = Arrays.copyOfRange(bytes,28,bytes.length);
		byte[] hashMessage = util.hashBytes(message);
		if(util.compareHash(hashSent,hashMessage))
			this.map.put(seqnum,message);
	}

	public byte[] createChunk()
	{
		byte[] out = new byte[bytefinish-bytestart+5]; // CHECK LATER
		byte[] start = ByteBuffer.allocate(4).putInt(bytestart).array();
		System.arraycopy(start,0,out,0,start.length);
		int index = 4;

		for(int i=0;i<numPackets;i++)
		{
			byte[] p = (byte[]) map.get(i);
			System.arraycopy(p,0,out,index,p.length);
			index += p.length;
		}

		return out;
	}

	public boolean isMissing()
	{
		for(int i=0;i<numPackets;i++)
		{
			if(map.get(i)==null)
				return true;
		}
		return false;
	}

	public byte[] prepareRange(int start,int finish)
	{
		int commandnumber = 10;
		byte[] cmd = ByteBuffer.allocate(4).putInt(commandnumber).array();
		byte[] fname = filename.getBytes();
		byte[] length = ByteBuffer.allocate(4).putInt(fname.length).array();
		byte[] begin = ByteBuffer.allocate(4).putInt(start).array();
		byte[] end = ByteBuffer.allocate(4).putInt(finish).array();

		byte[] out = new byte[16+fname.length];

		for(int i=0;i<4;i++)
		{
			out[i] = cmd[i];
			out[4+i] = length[i];
			out[8+i] = begin[i];
			out[12+i] = end[i];
		}
		System.arraycopy(fname,0,out,16,fname.length);

		return out;
	}
}
