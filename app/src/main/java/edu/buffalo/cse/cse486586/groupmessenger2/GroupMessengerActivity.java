package edu.buffalo.cse.cse486586.groupmessenger2;


import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static ArrayList<String> REMOTE_PORTS = new ArrayList<String>(Arrays.asList("11108", "11112", "11116", "11120", "11124"));
    static final int SERVER_PORT = 10000;
    static  int activeClients =5;
    int seqNum = 0;
    int msgCounter=1;
    int seqCounter=1;

    private String sendingPort=null; // Need to use for unicast sending

    private Uri providerUri=Uri.parse("content://edu.buffalo.cse.cse486586.groupmessenger2.provider");

    // Sender side processing
    HashMap<String,Integer> proposalTracker= new HashMap<String, Integer>();
    HashMap<String, ArrayList<Double>> proposalList= new HashMap<String,ArrayList<Double>>();

    //Receiver side processing
    HashMap<String,String> receivedMsgList= new HashMap<String, String>();
    PriorityBlockingQueue<MessageData> finalQueue= new PriorityBlockingQueue<MessageData>();
    static final String msgDelimiter ="#@#";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);


        /*
         * Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        sendingPort=myPort; //saving value to use later for unicasting

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Server Socket IOException from "+ myPort);
            return;
        } catch (Exception e){
            Log.e(TAG, "Server Socket Exception from "+ myPort);
        }

        final EditText editText = (EditText) findViewById(R.id.editText1);

        final Button Send = (Button) findViewById(R.id.button4);

        Send.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Log.i(TAG,"Send button is clicked ==> ");
                String msg = editText.getText().toString();
                editText.setText(""); // This is one way to reset the input box.
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort, String.valueOf(msgCounter));
                String prpcounter= myPort+ "_" + msgCounter;
                //Initializing variables
                proposalTracker.put(prpcounter,0);
                proposalList.put(prpcounter,new ArrayList<Double>());
                msgCounter++;
            }
        });
    }




    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            String resultMsg=null;

            String msgReceived=null;
            //    String[] msgs=null;
            Log.i("ServerSocket","Server processing start");

            try {
                while(true) {
                    //Need to create a socket to accept the connection from client
                    Socket clientSocket = serverSocket.accept();
                    InputStream is = clientSocket.getInputStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    String clientMsg;
                    if((clientMsg = br.readLine()) != null){
                        msgReceived = new String(clientMsg);


                    }
                    br.close();
                    clientSocket.close();//Closing the socket after reading full message

                    String[] msgs = msgReceived.split(msgDelimiter);

                    //    Log.i(TAG,"Server Task processing ==> "+ msgReceived);
                    Log.i("msgs 0","value of msg"+msgs[0]);
                    if (msgs[0].equals("MULTICAST")) {

                        Log.i("In if block","its multicast message");
                        Log.i(TAG,"Server Task Case MULTICAST Msgdata ==>" + msgs[2]);
                        receivedMsgList.put(msgs[1], msgs[2]);

                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgs[1]);

                        Log.i(TAG,"Server Task Case MSG End ");
                    }
                    else if (msgs[0].equals("PROPOSAL")){
                        Log.i(TAG,"Server Task Case Prp ");
                        int trackCount  = proposalTracker.get(msgs[1]) + 1;
                        ArrayList<Double> tempList = proposalList.get(msgs[1]);
                        if (trackCount < activeClients) {
                            //Waiting for all receivers to reply
                            tempList.add(Double.parseDouble(msgs[2]));
                            Log.i(TAG,msgs[1]+"==>"+ tempList.toString());
                            proposalList.put(msgs[1], tempList);
                            proposalTracker.put(msgs[1], trackCount);
                        } else {
                            //If all receivers replied calling clint task
                            Double agreedSeqNo = Collections.max(tempList);
                            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgs[1], String.valueOf(agreedSeqNo));
                        }
                        Log.i(TAG,"Server Task Case Prp END ");
                    }
                    else if(msgs[0].equals("AGREEMENT")){

                        Log.d(TAG,"Server Task Case ARG END");
                        //Notification for agreement
                    }
                    else{
                        Log.d(TAG,"In else block of server task");

                    }

                }
            } catch(SocketTimeoutException ste){
                Log.e(TAG, "Server Task IOException ==>" + ste.getMessage());
            } catch(SocketException se){
                Log.e(TAG, "Server Task IOException ==>" + se.getMessage());
            } catch (IOException ie) {
                Log.e(TAG, "Server Task IOException ==>" + ie.getMessage());
            } catch (Exception e){
                Log.e(TAG, "Server Task Exception ==>" + e.getMessage());
            }
            //   Log.i(TAG,"Server Task end with result ="+ resultMsg);
            return null;
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */

            String strReceived = strings[0].trim();
            TextView localTextView = (TextView) findViewById(R.id.textView1);
            localTextView.append(strReceived);
            localTextView.append("\n");

        }
    }


    private void insertDataToFile(String msgData){
        //    Log.i("insertdata", "In insert data method");
        ContentValues keyValueToInsert = new ContentValues();
        keyValueToInsert.put("key", Integer.toString(seqNum++));
        keyValueToInsert.put("value",msgData);
        getContentResolver().insert(providerUri, keyValueToInsert);
        //  Log.i("insertdata","After inserting data successully");
    }




    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko
     *
     */


    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            String msgToSend = null;
            String currentSendingPort=null;
            try {
                Socket socket;
                PrintStream ps;

                if(msgs.length==2 || msgs.length==3){
                    if(msgs.length==2){
                        msgToSend = "AGREEMENT" + msgDelimiter + msgs[0] + msgDelimiter + msgs[1];
                    }
                    else if(msgs.length==3){
                        msgToSend = "MULTICAST"+ msgDelimiter + msgs[1]+ "_"+ msgs[2] + msgDelimiter + msgs[0];
                    }

                    int length = activeClients;
                    for(int i =0; i<length;i++ ) {
                        socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(REMOTE_PORTS.get(i)));
                        currentSendingPort = REMOTE_PORTS.get(i);
                        socket.setSoTimeout(500);
                        ps = new PrintStream(socket.getOutputStream());
                        ps.print(msgToSend);
                        socket.close();
                    }
                }


                else if(msgs.length==1){
                    //     Log.i(TAG,"In else if block for proposal length 1");
                    //     Log.i(TAG,"Message length for proposal"+msgs.length);
                    String[] str = msgs[0].split("_");
                    String senderPort = str[0];
                    String strSeqNum;

                    //   Log.i(TAG, "Proposal Client Socket Chk1 testing ==>" +msgs[0]);

                    if(!sendingPort.equals(null)) {
                        strSeqNum = seqCounter++ + "." + sendingPort;
                    }else
                        strSeqNum= seqCounter++ +".0";

                    MessageData objMessageData= new MessageData(Double.parseDouble(strSeqNum),msgs[0],receivedMsgList.get(msgs[0]),false);
                    //     Log.i("MessageQueue","data"+objMessageData);
                    finalQueue.add(objMessageData);
                    PriorityBlockingQueue<MessageData> deliveryQueue = new PriorityBlockingQueue<MessageData>(finalQueue);
                    seqNum=0;
                    for(MessageData tempMsgData; (tempMsgData=deliveryQueue.poll())!=null;){
                        insertDataToFile(tempMsgData.getMsgData());
                    }
                    msgToSend = "PROPOSAL"+ msgDelimiter +msgs[0] + msgDelimiter + strSeqNum; //PRP delimt SenderPort_SenderMsgCount delimt proposedNum
                    Log.i(TAG, "ProposalClientSocket msg Chk 2 ==>" +msgToSend);
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(senderPort));
                    currentSendingPort= senderPort;
                    socket.setSoTimeout(500);
                    ps = new PrintStream(socket.getOutputStream());
                    ps.print(msgToSend);
                    socket.close();

                }
            }catch(SocketTimeoutException ste){
                Log.e(TAG, "SocketTimeout Exception::"+currentSendingPort);
            } catch(SocketException se){
                Log.e(TAG, "Socket Exception ::"+currentSendingPort);
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException ::"+currentSendingPort);
            } catch (IOException ie) {
                Log.e(TAG, "ClientTask socket IOException ::"+currentSendingPort);
            } catch(Exception e){
                Log.e(TAG, e.getMessage());
            }
            return null;
        }

    }
    private class MessageData implements Comparable<MessageData> {
        double seqNum;
        String clientPort;
        String msgData;
        boolean isDeliverable;

        public MessageData(double seqNum, String clientPort, String msgData, boolean isDeliverable) {
            this.seqNum = seqNum;
            this.clientPort = clientPort;
            this.msgData = msgData;
            this.isDeliverable = isDeliverable;
        }

        public String getMsgData() {
            return msgData;
        }

        @Override
        //This method used to compare data and store it in final queue
        //refered to https://stackoverflow.com/questions/3718383/why-should-a-java-class-implement-comparable for examples on implementing comparable class

        public int compareTo(MessageData other) {
            int lhs_port = Integer.parseInt(this.clientPort.split("_")[0]);
            int rhs_port = Integer.parseInt(other.clientPort.split("_")[0]);
            int lhs_count = Integer.parseInt(this.clientPort.split("_")[1]);
            int rhs_count = Integer.parseInt(other.clientPort.split("_")[1]);

            if(lhs_port > rhs_port) {
                return 1;
            } else if (lhs_port < rhs_port) {
                return -1;
            } else if (lhs_count > rhs_count) {
                return 1;
            } else if (lhs_count < rhs_count) {
                return -1;

            }

            return 0;
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }




}

