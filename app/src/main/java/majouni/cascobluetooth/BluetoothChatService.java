package majouni.cascobluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Button;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by majouni on 2/03/15.
 */
public class BluetoothChatService {
    //nombre del SDP record cuand creamos el socket del servidor
    private static final String NAME_SECURE = "BluetoothChatSecure";

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mSecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private Button mSendButton;

    //constantes que indican el estado de la conexion actual
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    public BluetoothChatService(Context context, Handler handler) {
        Log.d("B.chat.serv-1", "BluetoothChatService-le pasamos el contexto y el handler)");
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    /**
     * Set the current state of the chat connection
     *
     * @param state An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        Log.d("B.chat.serv-2", "setState- synchronized void");

        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        Log.d("B.chat.serv-3", "getState- synchronized int (return del estado de la conexion");

        return mState;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        Log.d("B.chat.serv-4", "start- synchronized void (abrimos el servicio de chat)");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            Log.d("B.chat.serv-5", "start-if1(mConnectThread) (cancelamos todos los intentos de abrir conexion con thread)");

            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            Log.d("B.chat.serv-6", "start-if2(mConnectedThread) (cancelamos todos los threads con connexion)");

            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_LISTEN);

        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            Log.d("B.chat.serv-6", "start-if3(mSecureAcceptThread) (empezar el thread para escuchar el BluetoothServerSocket)");

            mSecureAcceptThread = new AcceptThread(true);
            mSecureAcceptThread.start();
        }
    }


    private class AcceptThread extends Thread {

        // The local server socket
        private final BluetoothServerSocket mmServerSocket;
        private String mSocketType;


        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            try {
                Log.d("B.chat.serv-7", "AcceptThread-AcceptThread-try");
                tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,
                            MY_UUID_SECURE);
            } catch (IOException e) {
                Log.d("B.chat.serv-8-ERROR", "AcceptThread-AcceptThread-catch");

            }
            mmServerSocket = tmp;
        }

        public void run() {

            Log.d("B.chat.serv-9", "AcceptThread-run()");

            setName("AcceptThread Secure");

            BluetoothSocket socket = null;

            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                try {
                    Log.d("B.chat.serv-10", "while- escuchar el socket del servidor si no estamos conectados");
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.d( "B.chat.serv-11-ERROR", "Socket Type: " + mSocketType + "accept() failed");
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    Log.d( "B.chat.serv-12", "if-si se ha aceptado una conexion");

                    synchronized (BluetoothChatService.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
                                connected(socket, socket.getRemoteDevice(),
                                        mSocketType);
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.d("B.chat.serv-13", "Could not close unwanted socket");
                                }
                                break;
                        }
                    }
                }
            }
            Log.d("B.chat.serv-14", "END mAcceptThread, socket Type: " + mSocketType);

        }

        public void cancel() {
            try {
                Log.d("B.chat.serv-15", "cancel-try-Socket Type" + mSocketType + "cancel " + this);

                mmServerSocket.close();
            } catch (IOException e) {
                Log.d("B.chat.serv-16", "cancel-catch-Socket Type" + mSocketType + "close() of server failed");
            }
        }
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {

        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                Log.d("B.chat.serv-17", "ConnectThread-principio-try");

                    tmp = device.createRfcommSocketToServiceRecord(
                            MY_UUID_SECURE);

            } catch (IOException e) {
                Log.d("B.chat.serv-18", "ConnectThread-principio-catch");
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.d("B.chat.serv-19", "ConnectThread-run()");
            setName("ConnectThread" + "Secure");

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                Log.d("B.chat.serv-20", "ConnectThread-run()-try crear conexion con BluetoothSocket");

                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    Log.d("B.chat.serv-21", "ConnectThread-run()-try-catch-try cerrar socket: " + e);

                    mmSocket.close();
                } catch (IOException e2) {
                    Log.d("B.chat.serv-22", "ConnectThread-run()-try-catch-catch");

                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothChatService.this) {
                Log.d("B.chat.serv-23", "ConnectThread-run()-synchronized resetea el thread porque ya hemos acabado");

                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                Log.d("B.chat.serv-24", "ConnectThread-cancel()-try");

                mmSocket.close();
            } catch (IOException e) {
                Log.d("B.chat.serv-25", "ConnectThread-cancel()-catch");
            }
        }
    }


    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        Log.d("B.chat.serv-26", "connected-princpio");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            Log.d("B.chat.serv-27", "connected-if1(mConnectThread) cancela el thread que ha completado la conexion");

            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            Log.d("B.chat.serv-28", "connected-if2(mConnectedThread) cancela cualquier thread que tenga ya un conexion");

            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            Log.d("B.chat.serv-29", "connected-if3(mSecureAcceptThread) cancela el thread aceptado porque solo queremos conectar con un dispositivo");

            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }


    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d("B.chat.serv-30", "ConnectedThread-princpio");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                Log.d("B.chat.serv-31", "ConnectedThread-try coger los inputs y outputs de los Streams de BluetoothSocket");

                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.d("B.chat.serv-32", "ConnectedThread-catch");
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.d("B.chat.serv-33", "ConnectedThread-run()");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    Log.d("B.chat.serv-34", "ConnectedThread-run()-try escucha los InputStream mientras esta conectado");

                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    Log.d("B.chat.serv-35", "ConnectedThread-run()-catch conexion perdida, volver a empezar el servicio otra vez, restart");
                    connectionLost();
                    // Start the service over to restart listening mode
                    BluetoothChatService.this.start();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                Log.d("B.chat.serv-36", "ConnectedThread-write-try escribir al OutStream conectado");

                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {
                Log.d("B.chat.serv-37", "ConnectedThread-write-catch");
            }
        }

        public void cancel() {
            try {
                Log.d("B.chat.serv-38", "ConnectedThread-cancel()-try");

                mmSocket.close();
            } catch (IOException e) {
                Log.d("B.chat.serv-39", "ConnectedThread-cancel()-catch");
            }
        }
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        Log.d("B.chat.serv-40", "connectionLost()");

        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        Message msg2 = mHandler.obtainMessage(Constants.MESSAGE_CAMBIO_BUTTON);
        mHandler.sendMessage(msg2);


        // Start the service over to restart listening mode
        BluetoothChatService.this.start();
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        Log.d("B.chat.serv-41", "connectionFailed()");

        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        BluetoothChatService.this.start();
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        Log.d("B.chat.serv-42", "write()-principio escribir al ConnectedThread de manera asincrona");

        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            Log.d("B.chat.serv-43", "write()-synchronized sincroniza una copia del ConnectedThread");

            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
            * Stop all threads
    */
    public synchronized void stop() {
        Log.d("$$ STOP $$$", "stop");

        if (mConnectThread != null) {
            Log.d("B.chat.serv-43", "stop()-if1(mConnectThread) para el thread");

            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            Log.d("B.chat.serv-44", "stop()-if2(mConnectedThread) para el thread");

            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mSecureAcceptThread != null) {
            Log.d("B.chat.serv-45", "stop()-if3(mSecureAcceptThread) para el thread");

            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }
        setState(STATE_NONE);
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device, boolean secure) {
        Log.d("B.chat.serv-46", "connect()-principio");

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            Log.d("B.chat.serv-47", "connect()-if1 cancela cualquier thread intentando crear conexion");

            if (mConnectThread != null) {
                Log.d("B.chat.serv-48", "connect()-if1-if1(mConnectThread) cancela cualquier thread intentando crear conexion");

                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            Log.d("B.chat.serv-49", "connect()-if2(mConnectedThread) cancela cualquier thread que este actualmente conectado");

            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

}

