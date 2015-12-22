package bluetoothlibrary.network;

import android.app.Service;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

/**
 * Created by Tobias-Wolfgang Otto
 */
public abstract class BTNetworkConnection implements ServiceConnection {

    private Service service;

    public void onServiceConnected(ComponentName name, IBinder service) {
        this.service = ((BTNetworkService.LocalBinder) service).getService();
    }

    public void onServiceDisconnected(ComponentName name) {
        throw new RuntimeException("bluetooth service disconnected unexpectedly");
    }

    // Callback for listener
    public void onServiceReady() {
        onServiceReady(service);
        //service = null;
    }

    /**
     * Override this method to get notified when the service is ready to use.
     *
     * @param service The service object.
     */
    public abstract void onServiceReady(Service service);

}
