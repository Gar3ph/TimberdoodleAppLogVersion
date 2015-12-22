package de.tu_darmstadt.adtn.logging;

import android.content.Context;

import static de.tu_darmstadt.adtn.logging.utils.Constants.CREATEPAIR;
import static de.tu_darmstadt.adtn.logging.utils.Constants.RECEIVEPAIR;
import static de.tu_darmstadt.adtn.logging.utils.Constants.SELECTPAIR;
import static de.tu_darmstadt.adtn.logging.utils.Constants.SENDPAIR;

/**
 * Created by Tobias-Wolfgang Otto
 */
public class SendLogs {

    private Context context;

    public SendLogs(Context context) {
        this.context = context;
    }

    public void sendAll() {
        Thread create = new Thread(new SendLogFile(context, CREATEPAIR));
        create.start();
        Thread select = new Thread(new SendLogFile(context, SELECTPAIR));
        select.start();
        Thread send = new Thread(new SendLogFile(context, SENDPAIR));
        send.start();
        Thread receive = new Thread(new SendLogFile(context, RECEIVEPAIR));
        receive.start();
    }
}
