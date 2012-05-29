
import java.util.prefs.Preferences;
import java.util.zip.*;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.File;

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class JWiiLoad {
	static Socket socket;
	private static Preferences prefs = Preferences.userRoot().node("/com/vgmoose/jwiiload");

	// host and port of receiving wii (use 4299 and your own ip) also the .dol
	private static final int    port = 4299;
	//	private static final String host = "192.168.1.105";
	private static File filename; // = new File("/Users/Ricky/Downloads/wiimod_v3_0/card/app/wiimod/boot.dol");
	private static File compressed;
	private static String arguments ="";
	
	static String host;
	static String ip;

	static String lastip = prefs.get("host", "null");
	static boolean autosend = prefs.getBoolean("auto", true);

	static boolean cli = false;

	public static void main(String[] args) 
	{
		if (args.length==1)
		{
			System.out.println("JWiiload .9\ncoded by VGMoose, based on wiiload by dhewg\n\nusage:\n\tjava -jar JWiiload.jar <address> <filename> <application arguments>\n\npass $WIILOAD as the first argument if your environment is set up that way.\n\npass \"AUTO\" as the first argument to try to automatically find the Wii.\n\npass \"PREV\" as the first argument to use the last known Wii IP that worked.\n");
			System.exit(27);
		}
		if (args.length!=0)
		{
			System.out.println("Welcome to JWiiload CLI!");
			cli = true;
			
			filename = new File(args[1]);
			if (!filename.exists())
			{
				System.out.println("File at "+filename.getAbsolutePath()+" not found!");
				System.exit(2);
			}

			if (args[0].startsWith("tcp:"))
				args[0]=args[0].substring(4);

			if (args[0].equals("PREV"))
			{
				if (lastip.equals("null"))
				{
					System.out.println("There is no known previous working IP stored in this machine!");
					args[0]="AUTO";
				}
				else
					args[0]=lastip;
			}

			if (args[0].equals("AUTO"))
			{	
				tripleScan();
				if (host==null)
				{
					System.out.println("Could not find Wii through auto-detection");
					System.exit(3);
				}
				else if (host.equals("rate"))
				{
					System.out.println("Too many wireless requests to auto-detect, try again later.");
					System.exit(4);
				}
				else
					args[0]=host;
			}


			System.out.println("\nIP: "+args[0]);
			host = args[0];

			System.out.println("File: "+filename.getName());

			if (args.length>2)
				for (int x=2;x<args.length;x++)
				{
					arguments+=args[x];
					if (x!=args.length-1)
						arguments+=" ";
				}

			System.out.print("Arguments: "+arguments);
			
			if (arguments.length()==0)
				System.out.print("none");
			
			System.out.println("\n");
			
			if (socket==null)
				if (!connects())
					System.exit(1);

		}
		else
		{
			do{
				filename = GUI.chooseFile();
			}while(filename==null);

			GUI.createWindow();		// Create the JFrame GUI

		}

		if (filename!=null)
		{
			//button5.setEnabled(true);
			if (!cli)
				GUI.button5.setText("Send "+filename.getName());
		}

		if (filename!=null)
			compressData();	

		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				if (compressed!=filename)
					compressed.delete();
			}
		}));

		if (args.length==0)
			tripleScan();

		if (filename!=null)
			wiisend();

	}

	public static boolean connects()
	{
		System.out.println("Connecting to "+host+"...");
		
		try{
			socket = new Socket(host, port);
			System.out.println("Connection successful!\n");
			return true;
		} catch (Exception e) {
			System.out.println("Connection failed.");
			return false;
		}
		
	}
	
	public static void compressData()
	{
		try
		{
			// Compress the file to send it faster
			updateLabel("Compressing data...");
			System.out.println("Compressing data...");
			compressed = compressFile(filename);
			updateLabel("Data compressed!");
			System.out.println("Compression successful! ("+(int)(100*((compressed.length()+0.0)/filename.length()))+"% smaller)\n");

		} catch(Exception e){
			// Fall back in case compressed file can't be written
			System.out.println("Compression failed! Not going to send it compressed.\n");
			compressed = filename;
		}
	}

	public static void tripleScan()
	{
		for (int x=0; x<4; x++)
		{
			scan(x);
			if (host!=null)
				break;
		}
	}

	public static void wiisend()
	{

		try
		{
			// Open socket to wii with host and port and setup output stream
			System.out.println("Greeting the Wii...");

			if (host==null)
				socket = new Socket(host, port);

			updateLabel("Talking to Wii...");

			OutputStream os = socket.getOutputStream();
			DataOutputStream dos = new DataOutputStream(os);

			updateLabel("Preparing data...");
			System.out.println("Preparing local data...");

			byte max = 0;
			byte min = 5;

			short argslength = (short) (filename.getName().length()+arguments.length()+1);

			int clength = (int) (compressed.length());  // compressed filesize
			int ulength = (int) (filename.length());	// uncompressed filesize

			// Setup input stream for sending bytes later of compressed file
			InputStream is = new FileInputStream(compressed);
			BufferedInputStream bis = new BufferedInputStream(is);

			byte b[]=new byte[128*1024];
			int numRead=0;

			updateLabel("Talking to Wii...");
			System.out.println("Perparing remote data...");

			dos.writeBytes("HAXX");

			dos.writeByte(max);
			dos.writeByte(min);

			dos.writeShort(argslength);

			dos.writeInt(clength);	// writeLong() sends 8 bytes, writeInt() sends 4
			dos.writeInt(ulength);

			//dos.size();	// Number of bytes sent so far, should be 16

			updateLabel("Sending "+filename.getName());
			System.out.println("Sending "+filename.getName()+"...");
			dos.flush();

			while ( ( numRead=bis.read(b)) > 0) {
				dos.write(b,0,numRead);
				dos.flush();
			}
			dos.flush();

			updateLabel("Talking to Wii...");
			if (arguments.length()!=0)
				System.out.println("Sending arguments...");
			else
				System.out.println("Finishing up...");

			dos.writeBytes(filename.getName()+"\0");

			String[] argue = arguments.split(" ");

			for (String x : argue)
				dos.writeBytes(x+"\0");

			updateLabel("All done!");
			System.out.println("\nFile transfer successful!");

			if (compressed!=filename)
				compressed.delete();


		}
		catch (Exception ce)
		{
			updateLabel("No Wii found");
			int a=0;

			if (host==null)
				host="";

			if (!cli)
			{
				if (host.equals("rate"))
					a = GUI.showRate();
				else
					a= GUI.showLost();
			}
			else
			{
				System.out.println("No Wii found at "+host+"!");
				System.exit(1);
			}
			
			if (a==0)
			{
				tripleScan();
				wiisend();
			}

		}
	}
	
	static void updateLabel(String s)
	{
		if (!cli)
			GUI.text1.setText(s);
	}

	static void scan(int t)
	{			
		host=null;

		updateLabel("Finding Wii...");
		System.out.println("Searching for a Wii...");
		String output = null;

		InetAddress localhost=null;

		try {
			localhost = InetAddress.getLocalHost();
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		// this code assumes IPv4 is used
		byte[] ip = localhost.getAddress();

		for (int i = 1; i <= 254; i++)
		{
			try
			{
				ip[3] = (byte)i; 
				InetAddress address = InetAddress.getByAddress(ip);

				if (address.isReachable(10*t))
				{
					output = address.toString().substring(1);
					System.out.print(output + " is on the network");

					// Attempt to open a socket
					try
					{
						socket = new Socket(output,port);
						System.out.println("and is potentially a Wii!");
						updateLabel("Wii found!");
						host=output;
						return;
					} catch (Exception e) {
						System.out.println();
						//e.printStackTrace();
					}

				}
			} catch (ConnectException e) {
				updateLabel("Rate limited");
				host="rate";
				e.printStackTrace();
				return;
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
		} 

		return;

	}

	public static File compressFile(File raw) throws IOException
	{
		File compressed = new File(filename+".wiiload.gz");
		InputStream in = new FileInputStream(raw);
		OutputStream out =
			new DeflaterOutputStream(new FileOutputStream(compressed));
		byte[] buffer = new byte[1000];
		int len;
		while((len = in.read(buffer)) > 0) {
			out.write(buffer, 0, len);
		}
		in.close();
		out.close();
		return compressed;
	}

}