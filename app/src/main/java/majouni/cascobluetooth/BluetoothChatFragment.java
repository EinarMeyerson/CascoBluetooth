package majouni.cascobluetooth;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;

/**
 * Created by majouni on 2/03/15.
 */
public class BluetoothChatFragment extends Fragment {


    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;


    private ListView mConversationView;
    private Button mSendButton;


    private String mConnectedDeviceName = null;
    private ArrayAdapter<String> mConversationArrayAdapter;
    private StringBuffer mOutStringBuffer;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothChatService mChatService = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
        //cogemos el bluetooth adapter local
        mBluetoothAdapter= BluetoothAdapter.getDefaultAdapter();
        //si el adaptador es  nulo entonces bluetooth no soportado
        if (mBluetoothAdapter == null)
        {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, "Bluetooth no disponible", Toast.LENGTH_LONG).show();
            activity.finish();
        }
        Log.d("B.Chat.Frag."," 1- OnCreate: cargamos el bluetooth adarper y comprovamos si es nulo");
    }

    @Override
    public void onStart() {
        super.onStart();

        //si BT no encendido pregunta para encenderlo
        //setupChar() sera llamada durante onActivityResult

        Log.d("B.Chat.Frag"," 2- OnStart: comprovamos si esta activado el bluetooth , sino solicitamos encerderlo");
        if (!mBluetoothAdapter.isEnabled()){
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
        else if (mChatService == null)
        {
            Log.d("B.Chat.Frag"," 3- OnStart: si BT encendido llamamos a la funcion stupChat()");
            setupChat();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d("B.Chat.Frag"," 4- onCreateView: inicializamos el layout fragment_bleutooth_chat");
        return inflater.inflate(R.layout.fragment_bleutooth_chat, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        mConversationView = (ListView) view.findViewById(R.id.in);
        //mOutEditText =(EditText) view.findViewById(R.id.edit_text_out);
        mSendButton = (Button) view.findViewById(R.id.button_send);
        Log.d("B.Chat.Frag"," 5- onViewCreated: inicializamos el boton, el listview y el editext del fragment_bleutooth_chat");
    }



    @Override
    public void onResume() {
        super.onResume();

        if (mChatService != null)
        {
            if (mChatService.getState() == BluetoothChatService.STATE_NONE)
            {
                mChatService.start();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
        }
    }

    private void setupChat() {

        Log.d("$$ Comprobando $$", "setupChat()");

        // inicializando el array adapter para el thread de caca combersacion
        mConversationArrayAdapter = new ArrayAdapter<String>(getActivity(), R.layout.message);

        mConversationView.setAdapter(mConversationArrayAdapter);

       // mOutEditText.setOnEditorActionListener(mWriteListener);

        // inicializa el boton de enviar con un clicklistener
        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    //String message = textView.getText().toString();
                    String message ="1";
                    sendMessage(message);
                }
            }
        });

        //inicializamos el BluetoothChatService para realizar las conexiones Bluetooth
        mChatService = new BluetoothChatService(getActivity(), mHandler);
        Log.d("B.Chat.Frag"," 6 -setupChat(): Inicializamos el B.Chat.Service");

        // Initialize the buffer for outgoing messages
        //inicializa el buffer para los mensajes de salida
        mOutStringBuffer = new StringBuffer("");
    }


    /**
     * Makes this device discoverable.
     */
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Enviar mensajes
     *
     * @param message A string of text to send.
     */
    private void sendMessage(String message) {
        // comprueva que estamos conectados antes de intentar nada
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // comprueva que hay algo que enviar
        if (message.length() > 0) {
            // codifica el mensaje en bytes y se lo traspasa al bluethot service
            byte[] send = message.getBytes();
            mChatService.write(send);
            Log.d("B.Chat.Frag"," 7 -sendMessage(): pasamos el mensaje codificado en bytes al B.Chat:Service (funcion write)");

        }
    }

    /**
     * The action listener for the EditText widget, to listen for the return key
     */
    private TextView.OnEditorActionListener mWriteListener
            = new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message

            Log.d("B.Chat.Frag"," 8 - OnEditorActionListener: ni zorra de que hace esto");
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                Log.d("B.Chat.Frag"," 9 - OnEditorActionListener: diria que manda el mensaje");
                sendMessage(message);
            }
            return true;
        }
    };


    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d("B.Chat.Frag"," 10 - Handler: gestiona los mensajes que le pasa el B.Chat.Service");
            FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            Log.d("#### pruevas ###", "Comprovanco estado de la conexion");
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            mConversationArrayAdapter.clear();
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            Log.d("#### pruevas ###", "Comprovanco estado de la conexion: Conectado");
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE:
                            Log.d("#### pruevas ###", "Comprovanco estado de la conexion : no conectado");
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    Log.d("B.Chat.Frag"," 11 - Handler: escrive el mensaje en el chat");
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);

                    mConversationArrayAdapter.add("Yo : " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    Log.d("B.Chat.Frag"," 11.2 - Handler: escrive el mensaje en el chat");

                    //sacar la fecha de actualizacon
                    Calendar c= Calendar.getInstance();
                    int hora = c.get(Calendar.HOUR_OF_DAY);
                    int min = c.get(Calendar.MINUTE);
                    int dia = c.get(Calendar.DAY_OF_MONTH);
                    int mes = c.get(Calendar.MONTH);
                    int año =c.get(Calendar.YEAR);
                    mConversationArrayAdapter.add(hora+":"+min+ " "+ dia + "/"+mes+1+"/"+año+ " :  " + readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Conectado con "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                        mSendButton.setBackgroundResource(R.drawable.round_button_sinc);

                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();

                    }
                    break;
                case Constants.MESSAGE_CAMBIO_BUTTON:
                    mSendButton.setBackgroundResource(R.drawable.round_button_nosinc);
                    break;

            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d("$$ SEP $$", "BT not enabled");
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
        }
    }

    /**
     * Establish connection with other divice
     *
     *
     *
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        Log.d("B.Chat.Frag"," 11 - connectDevice: cogemos la MAC del dispositivo");
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.bluetooth_chat, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.secure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            }
            case R.id.discoverable: {
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
            }
        }
        return false;
    }

}