
import javax.swing.text.html.CSS;
import java.io.*;
import java.lang.System;
import java.net.Socket;
import java.net.UnknownHostException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;

// This is calvin's test commit
// This is an implementation of a simplified version of a command 
// line ftp client. The program always takes two arguments
//


public class CSftp
{
    static final int MAX_LEN = 255;
    static final int ARG_CNT = 2;

    public static void main(String [] args) throws IOException {
        byte cmdString[] = new byte[MAX_LEN];

        // Get command line arguments and connected to FTP
        // If the arguments are invalid or there aren't enough of them
        // then exit.

        if (args.length > ARG_CNT || args.length == 0) {
            System.out.print("Usage: cmd ServerAddress ServerPort\n");
            return;
        }

        //set default portNumber if not set to 21
        String hostName = args[0];
        int portNumber = 21;

        if (args.length == ARG_CNT){
            portNumber = Integer.parseInt(args[1]);
        }

        System.out.println(hostName + ", " + portNumber);

        try (Socket CSSocket = new Socket()){
            //this sets up 20 seconds connection timeout
            CSSocket.connect(new InetSocketAddress(hostName, portNumber), 20000);
            try (
                    PrintWriter out =
                            new PrintWriter(CSSocket.getOutputStream(), true);
                    BufferedReader in =
                            new BufferedReader(
                                    new InputStreamReader(CSSocket.getInputStream()));
            ) {
                BufferedReader stdIn =
                        new BufferedReader(
                                new InputStreamReader(System.in));
                Socket dataSocket = null;
                PrintWriter outData = null;
                BufferedReader inData = null;

                String fromServer;
                String fromUser = null;
                int dir = 0;
                int get = 0;
                String directory = "";


                while ((fromServer = in.readLine()) != null || fromUser == null) {
                    //System.out.println("i am back. " + fromServer);

                    // Server code response switches
                    if (fromServer.startsWith("421")) {
                        System.out.println("<-- " + fromServer);
                        throw new IOException("TimeOut");
                    } else if (fromServer.startsWith("121")){
                        System.out.println("<-- " + fromServer);
                        throw new IOException("TimeOut");
                    } else if (fromServer.startsWith("426")){
                        dir = 0; get = 0;
                        System.out.println("<-- " + fromServer);
                        System.err.println("0x3A7 Data transfer connection I/O error, closing data connection.");
                    } else if (fromServer.startsWith("550")){
                        dir = 0; get = 0;
                        System.out.println("<-- " + fromServer);
                    } else if (fromServer.startsWith("530")){
                        dir = 0; get = 0;
                        System.out.println("<-- " + fromServer);
                    } else if (fromServer.startsWith("211-")) {
                        System.out.println("<-- " + fromServer);
                        do {
                            System.out.println("<-- " + (fromServer = in.readLine()));
                        } while (!fromServer.startsWith("211 "));
                    } else if (fromServer.startsWith("221")) {
                        System.out.println("<-- " + fromServer);
                        System.out.println("Connection ended");
                        break;
                    } else if (fromServer.startsWith("227")) {
                        System.out.println("<-- " + fromServer);
                        String pasv = fromServer.substring(fromServer.indexOf("(") + 1);
                        pasv = pasv.substring(0, pasv.indexOf(")"));
                        //System.out.println(pasv);
                        String[] components = pasv.split(",");
                        String host = components[0] + "." + components[1] + "." + components[2] + "." + components[3];
                        int port = Integer.parseInt(components[4]) * 256 + Integer.parseInt(components[5]);
//					System.out.println("host: " + components[0] + "." + components[1] + "." + components[2] + "." + components[3]);
//					System.out.println(host);
//					System.out.println("port: " + Integer.parseInt(components[4]) * 256 + components[5]);
//					System.out.println(port);

                        try {
                            dataSocket = new Socket();
                            //this sets time out for tens seconds
                            dataSocket.connect(new InetSocketAddress(host, port), 10000);
                            outData = new PrintWriter(dataSocket.getOutputStream(), true);
                            inData = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
                            //System.out.println("DIR: " + dir);
                            if (dir == 1) {
                                //System.out.println("in dir.");
                                fromUser = "LIST";
                            } else if (get == 1) {
                                fromUser = "RETR";
                            }
                        } catch (SocketTimeoutException e) {
                            System.err.println("0x3A2 Data transfer connection to " + host + " on " + port + " failed to open.");
                            if (dataSocket != null){
                                dataSocket.close();
                            }

                            dir = 0;
                            get = 0;
                        } catch (IOException e) {
                            if (e.getMessage().contains("Connection refused")) {
                                System.err.println("0x3A2 Data transfer connection to " + host + " on " + port + " failed to open.");
                            } else {
                                System.err.println("0x3A7 Data transfer connection I/O error, closing data connection.");
                            }

                            if (dataSocket != null){
                                dataSocket.close();
                            }

                            dir = 0;
                            get = 0;

                        }
                        //System.out.println("<-- " + (fromServer = inData.readLine()));
                        //.out.println("outta try.");
                    } else if (fromServer.startsWith("150")) {
                        if (dir == 1) {
                            System.out.println("<-- " + fromServer);
                            while ((fromServer = inData.readLine()) != null) {
                                System.out.println("<-- " + fromServer);
                            }
                            System.out.println("<-- " + (fromServer = in.readLine()));
                        } else if (get == 1) {
                            String location[] = directory.split("/");
                            String fileName = location[location.length-1];
                            try {
                                BufferedInputStream inStream = new BufferedInputStream(dataSocket.getInputStream());
                                BufferedOutputStream outFile = new BufferedOutputStream(new FileOutputStream(new File(fileName)));
                                System.out.println("<-- " + fromServer);
                                byte[] buffer = new byte[4096];
                                int bytesRead = -1;

                                while ((bytesRead = inStream.read(buffer)) != -1) {
                                    outFile.write(buffer, 0, bytesRead);
                                }
                                inStream.close();
                                outFile.close();
                            }catch(IOException e){
                                System.err.println("0x38E Access to local file " + fileName + " denied");
                            }

                            System.out.println("<-- " + (fromServer = in.readLine()));
                        }

                        inData.close();
                        outData.close();
                        dataSocket.close();
                        dir = 0;
                        get = 0;

                    } else if (fromServer.startsWith("200")) {
                        System.out.println("<-- " + fromServer);
                        out.print("PASV\r\n");
                        out.flush();
//                    }
//
//                    /*----------- DELETE ----------------*/
//                    else if (fromServer.startsWith("331")) {
//                        out.print("pass anonymous" + "\r\n");
//                        out.flush();
//                        System.out.println("<-- " + (fromServer = in.readLine()));
//                        /*-------------DELETE---------------*/


                    } else {
                        System.out.println("<-- " + fromServer);
                    }
                    // End of server code response switches


                    // Start getting user command
                    if (dir != 1 && get != 1) {
                        System.out.print("csftp> ");
                        try {
                            fromUser = stdIn.readLine();
                        } catch (IOException e) {
                            System.err.println("0xFFFE Input error while reading commands, terminating.");
                            System.exit(1);
                            break;
                        }
                    }
                    boolean hasPrintedCsFTP = false;

                    // this covers
                    // "Empty lines and lines starting with the character '#' are to be silently ignored,
                    // and a new prompt displayed.
                    while (fromUser != null) {
                        if (fromUser.equals("") || fromUser.charAt(0) == '#') {
                            while (fromUser.equals("") || fromUser.charAt(0) == '#') {
                                if (!hasPrintedCsFTP) {
                                    System.out.print("csftp> ");
                                    hasPrintedCsFTP = true;
                                } else {
                                    System.out.print("csftp> ");
                                }
                                try {
                                    fromUser = stdIn.readLine();
                                } catch (IOException e) {
                                    System.err.println("0xFFFE Input error while reading commands, terminating.");
                                    System.exit(1);
                                    break;
                                }
                                hasPrintedCsFTP = false;
                            }
                        }

                        String[] userInput = fromUser.split(" ");
                        int validCommand = 0;
                        try {
                            // ERROR CATCHING IFs
                            // check # of argument satisfy preset application command
                            if (userInput[0].equalsIgnoreCase("quit") ||
                                    userInput[0].equalsIgnoreCase("features") ||
                                    userInput[0].equalsIgnoreCase("dir")) {

                                if (userInput.length > 1) {
                                    //System.out.println("catch INA1");
                                    throw new Exception("INA");
                                }
                            } else if (userInput[0].equalsIgnoreCase("user") ||
                                    userInput[0].equalsIgnoreCase("pw") ||
                                    userInput[0].equalsIgnoreCase("cd") ||
                                    userInput[0].equalsIgnoreCase("get")) {

                                if (userInput.length > 2 || userInput.length < 2) {
                                    //System.out.println("catch INA2.");
                                    throw new Exception("INA");
                                }
                            }
                            validCommand = 1;

                            // PROCESS VALID COMMANDS
                            if (fromUser.equalsIgnoreCase("quit")) {
                                out.print(fromUser.toUpperCase() + "\r\n");
                                out.flush();
                                //System.out.println("--> " + fromUser.toUpperCase());

                                /*---------DELETE------------------*/
//                            } else if (fromUser.startsWith("login")) {
//                                out.print("user anonymous" + "\r\n");
//                                out.flush();
//                                /*--------------DELETE-----------------*/
//

                            } else if (fromUser.startsWith("user ")) {
                                System.out.println("--> " + userInput[0].toUpperCase() + " " + userInput[1]);
                                out.print(fromUser.toUpperCase() + "\r\n");
                                out.flush();
                            } else if (fromUser.startsWith("pw ")) {
                                System.out.println("--> PASS " + userInput[1]);
                                String[] pw = fromUser.split(" ");
                                out.print("PASS " + pw[1] + "\r\n");
                                out.flush();
                                //System.out.println("--> " + fromUser.toUpperCase());
                            } else if (fromUser.equalsIgnoreCase("features")) {
                                System.out.println("--> FEAT");
                                out.print("FEAT" + "\r\n");
                                out.flush();
                            } else if (userInput[0].equalsIgnoreCase("get")) {
                                get = 1;
                                directory = userInput[1];
                                System.out.println("--> TYPE I");
                                out.print("TYPE I" + "\r\n");
                                out.flush();
                            } else if (userInput[0].equalsIgnoreCase("dir")) {
                                dir = 1;
                                System.out.println("--> PASV");
                                out.print("PASV" + "\r\n");
                                out.flush();
                            } else if (fromUser.startsWith("cd ")) {
                                System.out.println("--> CWD " + userInput[1]);
                                out.print("CWD " + userInput[1] + "\r\n");
                                out.flush();
                            } else if (fromUser.equalsIgnoreCase("list")) {
                                System.out.println("--> LIST");
                                out.print("LIST\r\n");
                                out.flush();
                            } else if (fromUser.equalsIgnoreCase("RETR")) {
                                out.print("RETR " + directory + "\r\n");
                                System.out.println("--> RETR " + directory);
                                out.flush();
                            } else {
                                throw new Exception("IC");
                            }

                            // Terminate inner while if command is valid
                            if (validCommand == 1) {
                                fromUser = null;
                            }

                        } catch (Exception e) {
                            if (e.getMessage().equalsIgnoreCase("IC")) {
                                System.err.println("0x001 Invalid command.");
                                System.out.print("csftp> ");
                                try {
                                    fromUser = stdIn.readLine();
                                } catch (IOException e3) {
                                    System.err.println("0xFFFE Input error while reading commands, terminating.");
                                    System.exit(1);
                                    break;
                                }                            }
                            if (e.getMessage().equalsIgnoreCase("INA")) {
                                System.err.println("0x002 Invalid number of arguments.");
                                System.out.print("csftp> ");
                                try {
                                    fromUser = stdIn.readLine();
                                } catch (IOException e4) {
                                    System.err.println("0xFFFE Input error while reading commands, terminating.");
                                    System.exit(1);
                                    break;
                                }
                            }
                            //System.out.println("2--> " + fromUser);
                        }
                    }
                    //System.out.println("break the inner while.");
                }
            }
            //System.out.println("fromServer is null.");
        } catch (SocketTimeoutException e) {
            System.err.println("0xFFFC Control connection to " + hostName + " on " + portNumber + " failed to open.");
            System.exit(1);
        } catch (IOException e) {
            if (e.getMessage().contains("Connection refused") || e.getMessage().contains("Timeout")){
                System.err.println("0xFFFC Control connection to " + hostName + " on " + portNumber + " failed to open.");
            } else {
                System.err.println("0xFFFD Control connection I/O error, closing control connection.");
            }
            System.exit(1);
        } catch (Exception e) {
            System.err.println("0xFFFF Processing error. " + e.getMessage());
            System.exit(1);
        }
    }
}



//	try {
//	    for (int len = 1; len > 0;) {
//		System.out.print("csftp> ");
//		len = System.in.read(cmdString);
//		if (len <= 0)
//		    break;
//		// Start processing the command here.
//		System.out.println("900 Invalid command.");
//	    }
//	    if(cmdString.equals("quit")){
//	    	System.out.println("QUIT");
//		}
//	} catch (IOException exception) {
//	    System.err.println("998 Input error while reading commands, terminating.");
//	}
